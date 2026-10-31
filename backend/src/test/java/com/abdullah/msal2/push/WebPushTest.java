package com.abdullah.msal2.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Web Push without a library: the payload decrypts the way a browser decrypts it (RFC 8291),
 * and the VAPID header verifies with the public key (RFC 8292).
 */
class WebPushTest {

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64D = Base64.getUrlDecoder();
	private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

	private static KeyPair p256() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		g.initialize(new ECGenParameterSpec("secp256r1"));
		return g.generateKeyPair();
	}

	private static byte[] d(KeyPair pair) {
		byte[] b = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
		byte[] out = new byte[32];
		int len = Math.min(b.length, 32);
		System.arraycopy(b, b.length - len, out, 32 - len, len);
		return out;
	}

	private final KeyPair vapid = p256();
	private final String vapidPublic = B64.encodeToString(WebPush.uncompressed((ECPublicKey) vapid.getPublic()));
	private final WebPush push = new WebPush(vapidPublic, B64.encodeToString(d(vapid)), "mailto:test@m-sal2.invalid",
			Clock.fixed(NOW, ZoneOffset.UTC));

	WebPushTest() throws Exception {
	}

	@Test
	void theBrowserCanDecryptThePayload() throws Exception {
		KeyPair device = p256();
		byte[] devicePublic = WebPush.uncompressed((ECPublicKey) device.getPublic());
		byte[] auth = new byte[16];
		new java.security.SecureRandom().nextBytes(auth);
		String text = "{\"notification\":{\"body\":\"سجّلت مصاريف اليوم؟\"}}";

		byte[] body = push.encrypt(text.getBytes(StandardCharsets.UTF_8), devicePublic, auth);

		assertThat(new String(decrypt(body, device, devicePublic, auth), StandardCharsets.UTF_8)).isEqualTo(text);
	}

	@Test
	void sendPostsWithVapidAndReadsTheAnswer() throws Exception {
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<String> encoding = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
			exchange.getRequestBody().readAllBytes();
			exchange.sendResponseHeaders(201, -1);
			exchange.close();
		});
		server.createContext("/gone", exchange -> {
			exchange.getRequestBody().readAllBytes();
			exchange.sendResponseHeaders(410, -1);
			exchange.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			KeyPair device = p256();
			String p256dh = B64.encodeToString(WebPush.uncompressed((ECPublicKey) device.getPublic()));
			String auth = B64.encodeToString(new byte[16]);

			assertThat(push.send(base + "/ok", p256dh, auth, "{}")).isEqualTo(WebPush.Outcome.SENT);
			assertThat(push.send(base + "/gone", p256dh, auth, "{}")).isEqualTo(WebPush.Outcome.GONE);
			assertThat(encoding.get()).isEqualTo("aes128gcm");

			// "vapid t=<jwt>, k=<public key>"
			String value = authorization.get();
			assertThat(value).startsWith("vapid t=").endsWith(", k=" + vapidPublic);
			String jwt = value.substring("vapid t=".length(), value.indexOf(','));
			String[] parts = jwt.split("\\.");
			Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
			verifier.initVerify(vapid.getPublic());
			verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
			assertThat(verifier.verify(B64D.decode(parts[2]))).isTrue();
			assertThat(new String(B64D.decode(parts[1]), StandardCharsets.UTF_8))
					.contains("\"aud\":\"" + base + "\"")
					.contains("\"exp\":" + (NOW.getEpochSecond() + 12 * 3600))
					.contains("\"sub\":\"mailto:test@m-sal2.invalid\"");
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void withoutKeysNothingIsSent() {
		WebPush off = new WebPush("", "", "mailto:x@y.invalid", Clock.systemUTC());
		assertThat(off.enabled()).isFalse();
		assertThat(off.publicKey()).isNull();
		assertThat(off.send("https://fcm.googleapis.com/x", "a", "b", "{}")).isEqualTo(WebPush.Outcome.FAILED);
	}

	@Test
	void onlyBrowserPushServicesAreAccepted() {
		assertThat(PushSubscriptions.validEndpoint("https://web.push.apple.com/QGx8")).isTrue();
		assertThat(PushSubscriptions.validEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
		assertThat(PushSubscriptions.validEndpoint("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
		assertThat(PushSubscriptions.validEndpoint("https://db5p.notify.windows.com/w/?token=x")).isTrue();
		assertThat(PushSubscriptions.validEndpoint("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
		assertThat(PushSubscriptions.validEndpoint("https://evil.example/fcm.googleapis.com")).isFalse();
		assertThat(PushSubscriptions.validEndpoint("https://fcm.googleapis.com.evil.example/x")).isFalse();
		assertThat(PushSubscriptions.validEndpoint("https://localhost:8080/admin")).isFalse();
		assertThat(PushSubscriptions.validEndpoint("https://fcm.googleapis.com:8443/x")).isFalse();
		assertThat(PushSubscriptions.validEndpoint(null)).isFalse();
	}

	/** What the browser does (RFC 8291 section 3). */
	private static byte[] decrypt(byte[] body, KeyPair device, byte[] devicePublic, byte[] auth) throws Exception {
		ByteBuffer in = ByteBuffer.wrap(body);
		byte[] salt = new byte[16];
		in.get(salt);
		assertThat(in.getInt()).isEqualTo(4096);
		byte[] serverPublic = new byte[in.get()];
		in.get(serverPublic);
		byte[] ciphertext = new byte[in.remaining()];
		in.get(ciphertext);

		ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(new BigInteger(1, Arrays.copyOfRange(serverPublic, 1, 33)),
				new BigInteger(1, Arrays.copyOfRange(serverPublic, 33, 65))),
				((ECPublicKey) device.getPublic()).getParams());
		PublicKey server = KeyFactory.getInstance("EC").generatePublic(spec);
		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(device.getPrivate());
		agreement.doPhase(server, true);
		byte[] secret = agreement.generateSecret();

		byte[] info = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), devicePublic, serverPublic);
		byte[] ikm = expand(hmac(auth, secret), info, 32);
		byte[] prk = hmac(salt, ikm);
		byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
		byte[] padded = cipher.doFinal(ciphertext);
		assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
		return Arrays.copyOf(padded, padded.length - 1);
	}

	private static byte[] hmac(byte[] key, byte[] data) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
		return Arrays.copyOf(hmac(prk, concat(info, new byte[] { 1 })), length);
	}

	private static byte[] concat(byte[]... parts) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.writeBytes(p);
		}
		return out.toByteArray();
	}
}
