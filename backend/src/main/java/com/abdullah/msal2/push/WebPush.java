package com.abdullah.msal2.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends Web Push messages (SCREENS.md section 7) with the JDK only: VAPID (RFC 8292) to say who
 * sends, and the payload encrypted for the device (RFC 8291, {@code aes128gcm}). No paid service.
 * <p>
 * The VAPID keys come from the environment (CLAUDE.md rule 16), as base64url: the public key as
 * the 65-byte uncompressed point, the private key as the 32-byte number. Without them this
 * server sends nothing and {@link #publicKey()} is null.
 */
@Component
public class WebPush {

	/** What the push service answered. */
	public enum Outcome {
		SENT,
		/** The device unsubscribed (404 or 410): forget the subscription */
		GONE,
		FAILED
	}

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64D = Base64.getUrlDecoder();
	private static final int RECORD_SIZE = 4096;

	private final String publicKey;
	private final ECPrivateKey privateKey;
	private final String subject;
	private final Clock clock;
	private final HttpClient http;
	private final SecureRandom random = new SecureRandom();

	public WebPush(@Value("${msal.push.vapid-public-key:}") String publicKey,
			@Value("${msal.push.vapid-private-key:}") String privateKey,
			@Value("${msal.push.subject:mailto:noreply@m-sal2.invalid}") String subject, Clock clock) {
		this.clock = clock;
		this.subject = subject;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		if (publicKey.isBlank() || privateKey.isBlank()) {
			this.publicKey = null;
			this.privateKey = null;
			return;
		}
		try {
			ECParameterSpec p256 = p256();
			this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC")
					.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, B64D.decode(privateKey.strip())), p256));
			this.publicKey = publicKey.strip();
			point(B64D.decode(this.publicKey), p256); // fails early on a broken key
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Invalid VAPID keys (msal.push.*)", e);
		}
	}

	/** The key a browser subscribes with, or null when push is not set up. */
	public String publicKey() {
		return publicKey;
	}

	public boolean enabled() {
		return publicKey != null;
	}

	/** Sends one encrypted message; never throws for the push service's answers. */
	public Outcome send(String endpoint, String p256dh, String auth, String payload) {
		if (!enabled()) {
			return Outcome.FAILED;
		}
		try {
			URI uri = URI.create(endpoint);
			byte[] body = encrypt(payload.getBytes(StandardCharsets.UTF_8), B64D.decode(p256dh), B64D.decode(auth));
			HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "vapid t=" + jwt(uri.getScheme() + "://" + uri.getAuthority()) + ", k="
							+ publicKey)
					.header("Content-Encoding", "aes128gcm")
					.header("Content-Type", "application/octet-stream")
					.header("TTL", "43200")
					.header("Urgency", "normal")
					.POST(HttpRequest.BodyPublishers.ofByteArray(body))
					.build();
			int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			if (status == 404 || status == 410) {
				return Outcome.GONE;
			}
			return status >= 200 && status < 300 ? Outcome.SENT : Outcome.FAILED;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Outcome.FAILED;
		}
		catch (Exception e) {
			return Outcome.FAILED;
		}
	}

	/** RFC 8291: one aes128gcm record, the content-coding header carrying our ephemeral key. */
	byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
		ECParameterSpec p256 = p256();
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"), random);
		KeyPair ephemeral = generator.generateKeyPair();
		byte[] asPublic = uncompressed((ECPublicKey) ephemeral.getPublic());

		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(ephemeral.getPrivate());
		agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(point(uaPublic, p256)), true);
		byte[] ecdhSecret = agreement.generateSecret();

		byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
		byte[] ikm = hkdfExpand(hmac(authSecret, ecdhSecret), keyInfo, 32);
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		byte[] prk = hmac(salt, ikm);
		byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] { 2 })); // 2 = the last record

		ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
		header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
		return concat(header.array(), ciphertext);
	}

	/** RFC 8292: an ES256 JWT for the push service's origin, valid 12 hours. */
	private String jwt(String audience) throws GeneralSecurityException {
		long exp = clock.instant().getEpochSecond() + 12 * 3600;
		String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
		String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject
				+ "\"}").getBytes(StandardCharsets.UTF_8));
		Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
		signer.initSign(privateKey);
		signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
		return header + "." + claims + "." + B64.encodeToString(signer.sign());
	}

	private static ECParameterSpec p256() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		return ((ECPublicKey) generator.generateKeyPair().getPublic()).getParams();
	}

	private static ECPublicKeySpec point(byte[] uncompressed, ECParameterSpec params) {
		if (uncompressed.length != 65 || uncompressed[0] != 4) {
			throw new IllegalArgumentException("Not an uncompressed P-256 point");
		}
		return new ECPublicKeySpec(new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
				new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65))), params);
	}

	static byte[] uncompressed(ECPublicKey key) {
		byte[] out = new byte[65];
		out[0] = 4;
		fixed32(key.getW().getAffineX(), out, 1);
		fixed32(key.getW().getAffineY(), out, 33);
		return out;
	}

	private static void fixed32(BigInteger n, byte[] out, int offset) {
		byte[] b = n.toByteArray();
		int len = Math.min(b.length, 32);
		System.arraycopy(b, b.length - len, out, offset + 32 - len, len);
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	/** HKDF-Expand for one block (every length here is at most 32). */
	private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
		return Arrays.copyOf(hmac(prk, concat(info, new byte[] { 1 })), length);
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.writeBytes(p);
		}
		return out.toByteArray();
	}
}
