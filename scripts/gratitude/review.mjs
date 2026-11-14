// The owner's review page of the gratitude texts (DESIGN.md D-42), made from the app's own list:
//   node scripts/gratitude/review.mjs
import fs from 'node:fs';
const src = 'frontend/src/app/features/gratitude/gratitude-texts.json';
const dest = 'docs/GRATITUDE-TEXTS.md';
const list = JSON.parse(fs.readFileSync(src, 'utf8'));

const where = (x) => (x.short && !x.salaryOnly ? 'بعد الإضافة وبطاقة الراتب' : 'بطاقة الراتب فقط');
const cell = (s) => String(s).replace(/\|/g, '\\|');
const section = (type, title) => {
  const rows = list.filter((x) => x.type === type).map((x) => {
    const notes = [x.part ? 'جزء من الآية' : '', x.salaryOnly && x.short ? 'نبرة تنبيه: بطاقة الراتب فقط' : '',
      x.source.startsWith('دعاء عام') ? 'ليس نصاً مأثوراً' : ''].filter(Boolean).join('، ');
    const text = type === 'آية' ? `﴿${x.text}﴾` : x.text;
    return `| ${x.id} | ${cell(text)} | ${cell(x.source)} | ${cell(x.grade)} | ${where(x)} | ${cell(notes)} |`;
  });
  return `## ${title} (${rows.length})\n\n| الرقم | النص | المصدر | الدرجة | يطلع في | ملاحظة |\n|---|---|---|---|---|---|\n${rows.join('\n')}\n`;
};
const md = `# نصوص الذكر والشكر (للمراجعة)

> **نصوص D-42** (DESIGN.md) كما في التطبيق: [gratitude-texts.json](../frontend/src/app/features/gratitude/gratitude-texts.json). هذه الصفحة مولّدة منه (\`node scripts/gratitude/review.mjs\`)، والآية الجديدة تُجلب بـ \`scripts/gratitude/verse.mjs\`.

**كيف جُمعت:**
- **الآيات** منقولة حرفياً من **نص Tanzil المبسّط** (\`quran-simple\` عبر api.alquran.cloud)، بلا أي تعديل، مع السورة ورقم الآية. "جزء من الآية" = كلمات متتالية من الآية كما هي.
- **الأدعية والأذكار من صحيحي البخاري ومسلم فقط**: أذكار الحمد والشكر على النعمة، والأذكار العامة التي لها صلة بالرزق فقط (قاعدة المالك). "مختصر" = جزء متصل من نص الحديث.
- **اللفظ وأرقام الأحاديث من معرفة Claude، وليست منقولة من مصدر**، فيلزم التحقق منها (مثلاً في الدرر السنية) قبل النشر.
- **يطلع في:** النص القصير (60 حرفاً أو أقل بدون تشكيل) يطلع بعد أي إضافة وفي بطاقة الراتب، والطويل في بطاقة الراتب فقط.
- **نبرة تنبيه:** آيات عن الإسراف أو قلة الشكر، قد تُفهم كلوم بعد كل مصروف، فتطلع في بطاقة الراتب فقط (\`salaryOnly\`).

**المجموع: ${list.length}** (${list.filter((x) => x.short).length} قصير، ${list.filter((x) => !x.short).length} طويل).

${section('آية', 'الآيات')}
${section('دعاء', 'الأدعية')}
${section('ذكر', 'الأذكار')}`;
fs.writeFileSync(dest, md);
console.log('written', list.length);
