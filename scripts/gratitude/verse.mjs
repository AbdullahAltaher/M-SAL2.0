// A verse for the gratitude texts (DESIGN.md D-42), exactly as in Tanzil's simple text, never
// typed by hand. Run without a range to see the words and their numbers, then with the first
// and last word to get the entry to add to gratitude-texts.json:
//   node scripts/gratitude/verse.mjs 14:7
//   node scripts/gratitude/verse.mjs 14:7 3 5
const [ref, from, to] = process.argv.slice(2);
if (!/^\d+:\d+$/.test(ref ?? '')) {
  console.error('Usage: node scripts/gratitude/verse.mjs <surah:ayah> [firstWord lastWord]');
  process.exit(1);
}
const res = await fetch(`https://api.alquran.cloud/v1/ayah/${ref}/quran-simple`);
if (!res.ok) throw new Error('Quran API: ' + res.status);
const ayah = (await res.json()).data;
const words = ayah.text.split(' ');
if (from === undefined) {
  console.log(words.map((w, i) => `${i}:${w}`).join('  '));
} else {
  const a = Number(from);
  const b = Number(to ?? words.length - 1);
  const entry = { id: '', type: 'آية', text: words.slice(a, b + 1).join(' '),
    source: `${'<اسم السورة>'}: ${ayah.numberInSurah}`, grade: '', short: false };
  if (a > 0 || b < words.length - 1) entry.part = true;
  console.log(JSON.stringify(entry, null, 1));
  console.log('Set id, the surah name, and short (60 letters or fewer without marks), then run review.mjs');
}
