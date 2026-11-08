# تسليم بين الجلسات

> **اقرأه أولاً، وحدّثه في نهاية كل جلسة.** مختصر عمداً: الحالة، وأين الكود، والفخاخ.
> **لتوفير التوكنز:** اقرأ فقط ما تحتاجه المهمة. `HANDOFF-HISTORY.md` (~70KB) ابحث فيه بـ `grep` ولا تقرأه كاملاً، و`API.md` و`BANK-SMS.md` و`DESIGN.md` اقرأ منها القسم المطلوب فقط (`grep -n "^#"` أولاً). خريطة الكود في القسم 4 تغني عن الاستكشاف.
> آخر تحديث: 2026-11-08.

## 1. المالك وطريقة العمل

- **العربي الخليجي**: الشرح بالعربي البسيط، والمصطلحات التقنية بالإنجليزي. يعرف Java وSpring وAngular وFlyway، وليس خبيراً في السيرفرات.
- **Claude يتحكم في كل شيء ممكن.** بيد المالك فقط: تسجيل الدخول للخدمات، وإنشاء الحسابات، والدفع، والموافقة على الشروط، وإعدادات Windows. لا يحب البحث في واجهات المواقع: استخرج القيم بأوامر.
- **commit وpush على `main` فقط بطلبه** (عادة: "نعم، commit push"). `main` ينشر التطبيق والسيرفر مباشرة.
- **أي تصميم جديد:** نقاش ونماذج مرئية قبل الكود (النماذج فكرة، لا الشكل النهائي). **طلب صغير وواضح منه** (مثل B-21) يُنفَّذ مباشرة ويُكتب قراره.
- **يجرّب على الآيفون ويرسل صوراً.** اشرح خطوات الآيفون واحدة واحدة بالأسماء الإنجليزية كما تظهر عنده.
- **الأسرار** في `.env` يضعها بنفسه: لا تطلبها ولا تطبعها. **المستودع عام:** لا أرقام ولا أسماء من بياناته الحقيقية في `docs/` أو الاختبارات أو الكود.

## 2. الحالة الآن

- **المراحل 0 إلى 11 مكتملة ومنشورة.** آخرها رسائل البنك ([BANK-SMS.md](BANK-SMS.md))، و**جرّبها المالك على الآيفون بعملية حقيقية ونجحت** (2026-11-08). بعدها B-21 (تغيير يوم الطلب) وحقل الكلمة داخل الفئة (`e03eb2e`)، وإصلاح نسب "حسب الفئة" في الإحصاءات (`4e639cd`، تعارض كلاس `.pc`).
- **التقرير الأكاديمي** (`docs/report/`، إنجليزي وعربي، `.md` و`.docx`) مكتوب حتى المرحلة 11. **خاص بالمالك: محلي فقط (`.gitignore`)، لا يُرفع.** لا يُقرأ في العمل العادي. التحويل لـ Word: `node docs/report/tools/to-docx.mjs jobs`، ثم `preview_start` باسم `report-tools` (يرسم المخططات والمعادلات، انتظر `DONE`)، ثم `node docs/report/tools/to-docx.mjs`. المعاينة: `http://localhost:4310/preview.html?f=M-SAL2.0-Report-AR.docx`.
- **بيد المالك الآن:** إضافة كلمات التصنيف من الإعدادات ← رسائل البنك (`CHARITY`، و`CAFE`...). بدون كلمات كل الطلبات "اختر الفئة".
- **سؤال مفتوح للمالك (لم يُقرَّر):** الإشعار يأتي باسم **Shortcuts** (الاختصار يعرض رد السيرفر، B-08). البديل المعروض: السيرفر يرسل **Web Push باسم التطبيق** (مثل التذكير اليومي، يفتح صفحة المراجعة)، ويُحذف Show Notification من الاختصار. يحتاج تفعيل الإشعارات على الجوال. وقلنا له يطفئ **Notify When Run** لإخفاء "Running your automation".
- **التالي: المرحلة 12 (دعوة الأهل والأصدقاء).** قرار معلّق: (1) **زر "مشاركة الدعوة"** في S-30 (`navigator.share` ونسخ) برسالة جاهزة: الرابط، والتثبيت، و"ادخل بإيميلك واكتب الرمز" **(الاقتراح، بدون أسرار جديدة)**، أو (2) إيميل دعوة عبر `POST /auth/v1/invite` بقالب عربي (يحتاج توكن Supabase). الآن الدعوة تُنشئ مستخدم Supabase بدون إيميل (`SupabaseAuthAdmin.createUser`). بعد القرار: شخص واحد أولاً.
- **لم يُجرَّب على الآيفون:** صورة البطاقة من الألبوم، وشاشة القفل مع Face ID، ومصاريف الفئة في الإحصاءات (D-39)، وصفحة المراجعة بالكامل (الاعتماد وB-21).
- **اقتراحات لاحقة (غير ضرورية):** تنبيهات Dependabot، وRLS على `flyway_schema_history` (في `V10`)، ومسح أرقام بياناته القديمة من تاريخ git (يحتاج `push --force`: قراره).

## 3. أين كل شيء

| الشيء | أين |
|---|---|
| التطبيق | https://abdullahaltaher.github.io/M-SAL2.0/ (GitHub Pages، `pages.yml` عند push على `main` في `frontend/**`) |
| السيرفر | https://m-sal2.onrender.com (Render Starter، Singapore، `render.yaml`، ينشر بعد نجاح CI على `main`، حوالي 3 دقائق) |
| قاعدة البيانات والدخول | Supabase مشروع `M-SAL2.0` (ref `bzdzveckpxmmdoqcfewv`)، Session pooler `aws-0-ap-southeast-1.pooler.supabase.com:5432` |
| الإيميلات (رمز الدخول) | Gmail المالك بـ App Password عبر `scripts/apply-auth-email.mjs`، والقالب `docs/supabase/otp-email.html` |
| النسخ الاحتياطي | مستودع خاص `M-SAL2.0-backups`، يومياً 03:30 دبي ([BACKUP.md](BACKUP.md)). القالب `ops/backup/` |
| CI | `ci.yml` على `main` وطلبات الدمج: `./mvnw -B -q verify`، و`npm test`، و`npm run build` |
| M-SAL القديم | لا يُلمس. نسخة للقراءة في `C:\Users\User\IdeaProjects\M-SAL` |

**المراجع:** `BUSINESS_RULES.md` (القسم 11 = أين يُختبر كل قاعدة)، و`API.md` (القسم 3 الأخطاء، و4 المسارات، و9 قرارات التنفيذ)، و`SCREENS.md` (أوله: فروق التصميم ورسائل البنك)، و`DESIGN.md` (`D-01` إلى `D-39`)، و`BANK-SMS.md` (القرارات `B-01` إلى `B-21` في القسم 3، والتنفيذ في 4.9).

## 4. خريطة الكود

- **السيرفر** `backend/src/main/java/com/abdullah/msal2/`، وحدة لكل خدمة (Controller ← Service ← Repository):
  `me` (SRV-000) · `expenses` (SRV-001، و`EntryService.home` فيها `pendingCount`) · `budget` (002) · `cycles` (003، الحسابات في `Cycles` و`CycleSummary`) · `accounts` (004) · `categories` (005) · `stats` (006) · `importer` (007) · `receipt` (008) · `users` (009) · `push` (التذكير اليومي) · `banksms` (011).
  `system/`: `security/SecurityConfig` (JWT)، و`access/ServiceAccessInterceptor` (الصلاحية والحدود)، و`error/ErrorCode` (كل الأكواد)، و`audit/AuditService`، و`time/Today` (اليوم بتوقيت المستخدم)، و`web/Inputs` (تحليل المبالغ والتواريخ)، و`web/RequestSizeFilter`.
  الجداول: `src/main/resources/db/migration/V1`–`V9`. **التالي `V10`.**
- **الاختبارات** `backend/src/test/...`: `IntegrationTest` (الأساس: Testcontainers، والساعة ثابتة 2026-11-11 08:00Z، و`signUp`، و`budgetOf`، و`accountOf`، و`categoryOf`)، و`system/IsolationTest`، و`banksms/FakeSms` (رسائل وهمية).
- **الواجهة** `frontend/src/app/`: `app.routes.ts` · `core/api/api.ts` و`models.ts` (كل المسارات والأشكال) · `core/session/session-store.ts` · `features/expenses/` (`home-view`، و`entries-view`، و`entry-sheet` + `entry-sheets.ts` تفتح كل النوافذ، و`expenses-store.ts`، و`pending-view.ts`) · `features/budget/` · `features/stats/` · `features/settings/` (`settings-sheet.ts`، و`bank-sms-sheet.ts`، و`shortcut-setup.ts`) · `shared/components/icon.ts` (الأيقونات) · `shared/sheet/` (النوافذ، و`Dialogs` تستبدل النافذة الحالية).
  الأنماط: `styles/tokens.scss` (الألوان فاتح وداكن)، و`styles/glass.css` (كل الشكل، والأقسام الجديدة في آخره).

## 5. التشغيل والتجربة

- **Git Bash:** `export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin:/c/Program Files/GitHub CLI"`، و`MSYS_NO_PATHCONV=1` قبل `docker run`. **لا Python:** استخدم Node. للمسارات مع Node استخدم `C:/Users/...` وليس `/c/Users/...`.
- **Docker Desktop** غالباً متوقف في جلسة جديدة: `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` (PowerShell)، وانتظر `docker info`. الاختبارات تحتاجه. `docker compose up -d --build`: قاعدة على **5433** وسيرفر على **8080** (5432 لـ Postgres آخر، لا تلمسه).
- **الاختبارات:** `backend/./mvnw -q -o test` (232، ~3 دقائق، شغّلها في الخلفية)، و`frontend/npx ng test --watch=false` (82)، و`npm run build` (الحزمة الأولى ~457KB، الحد 500KB). لاختبار واحد: `-Dtest='BankSmsApiTest'`.
- **الواجهة بدون دخول (الأسرع):** `preview_start` باسم `frontend`، ثم `resize_window` بـ `mobile`، ثم افتح `/login` ونفّذ بـ javascript_exec:
  `window.__mockGo = '/expenses/home'; await import('/dev/mock-api.js?' + Date.now())`
  الملف `frontend/dev/mock-api.js`: API وجلسة وهميان (`ng serve` فقط، ليس في البناء)، وحالته في `window.__mock` (`pending`، و`settings`، و`last` = آخر جسم أُرسل). أي إعادة تحميل (تعديل ملف) تمسحه: نفّذه مرة ثانية. مسار غير موجود فيه ← 404 و`mock: unmocked` في الـ console.
- **المتصفح المخفي** لا يرسم ويبطئ المؤقتات: `show_pane preview` قبل الصور، ولا تنتظر طويلاً داخل javascript_exec (يتوقف بعد 45 ثانية).
- **الدخول الحقيقي محلياً:** المالك بإيميله، أو `node scripts/dev-sign-in-link.mjs` **يشغّله المالك بنفسه**.
- **رسائل البنك بـ curl محلياً:** مستخدم وهمي في قاعدة Docker مع `sms_tokens.token_hash` = SHA-256 لرمز تولّده بـ Node (لا تطبعه)، ثم `POST http://localhost:8080/api/v1/sms/ingest`، ثم احذفه (`audit_log` ← `sms_tokens` ← `budgets` ← `user_roles` ← `app_users`).
- **بعد push:** `gh run list --limit 4` ثم `gh run watch <id>` (فلتر `--commit` بالـ SHA القصير لا يعمل).

## 6. فخاخ

- **`view-transition-name` يقطع الضبابية:** الاسم `page` على `.view` فقط أثناء الحركة. و`skipInitialTransition` لازم، والتغيير بلا حركة يُتخطى (`page-motion.ts`). "Transition was skipped" في الـ console طبيعي في التطوير.
- **الآيفون و`backdrop-filter`:** لا تكبّر عنصراً فوق بطاقة زجاجية بحركة طي (`Collapse`): أظهره مباشرة (`@if`).
- **أسماء كلاسات عامة** (`sheet`، و`row`، و`panel`، و`field`) مستخدمة في `ledger.css` و`glass.css`: استخدم بادئة (`pc-`، و`bs-`...). و`.g-hero` فيها `overflow:hidden`. **قبل أي كلاس جديد في `glass.css`:** `grep -rn '\.NAME\b\|"NAME"' frontend/src` (كلاس `.pc` لبطاقة رسائل البنك كسر نسب الإحصاءات، والحل كان `g-pct`).
- **أيقونة داخل `app-icon`** (`display: contents`): للتدوير استهدف `app-icon:last-child svg`، وليس `svg:last-child` (يطابق كل الأيقونات).
- **Spring:** `@Transactional` من service أخرى ثم التقاط الاستثناء يجعلها rollback-only (لذلك `CategoryService.find` يرجع `Optional`).
- **Guards:** `inject()` لا يعمل بعد `await`.
- **CSP في `index.html`:** أي عنوان خارجي جديد يُضاف لها.
- **التخزين في الجهاز:** مفاتيح `msal2.*` فقط، والصور في IndexedDB `msal2.photos`. **ممنوع أي `mizaniya`.**
- **`backend/mvnw`** يبقى `100755` في git. و**`docker --env-file`** يأخذ القيم حرفياً.
- **`SmsSecurityConfig`:** فلتر الرمز ليس `@Component` (وإلا يعمل على كل طلب)، ويمسح أي مصادقة، فـ `jwt()` في MockMvc لا يمر من `/sms/ingest`.
- **الساعة الثابتة في الاختبارات:** كل الطلبات بنفس `received_at`، فابحث بالجهة (`byMerchant`) لا بالترتيب. وحد `@StrictRateLimit` 10 في الدقيقة لكل مستخدم يشمل `/sms/ingest` و`/sms/token`.
- **ملفات CRLF** (`API.md`، و`CLAUDE.md`، و`SCREENS.md`، و`DATABASE.md`، و`angular.json`): التعديل بسكربت Node يحوّل لـ LF ثم يرجع، أو بأداة Edit. `sed` و`node -e` مع `\n` لا يطابقان.
- **السكربتات الطويلة:** لا تضعها في `node -e "..."` فيها backticks أو علامات تنصيص عربية: استخدم `node - <<'EOF'` أو ملفاً في scratchpad.
- **قاعدة Docker المحلية فيها `V9`:** لا يُعدَّل أي migration مطبَّق؛ الجديد `V10`.

## 7. الأسرار والتوكنات

- **توكن Supabase** (`SUPABASE_ACCESS_TOKEN`) عند الحاجة فقط (إعدادات Auth): ينشئه المالك من https://supabase.com/dashboard/account/tokens بصلاحيتي **Project Settings** و**Auth Config** (Read and write)، محصوراً على M-SAL2.0، في `.env`، و**يُحذف بعد الاستخدام**.
- **مفاتيح VAPID** لا تُنشأ مرة ثانية (تلغي اشتراك كل الأجهزة). `VAPID_SUBJECT` = عنوان التطبيق.
- **إذا تغيّرت كلمة مرور Google أو أُلغي App Password:** تتوقف رموز الدخول. الحل: App Password جديد في `.env`، ثم `scripts/apply-auth-email.mjs`.
- **رمز الاختصار** (رسائل البنك) عند المالك فقط، وفي القاعدة بصمته SHA-256. إذا ضاع: "إنشاء رمز جديد" وتحديث الاختصار.
- **ذاكرة Render:** حوالي 290 إلى 355MB من 512MB (محلياً 309MB بعد المرحلة 11). عند 450MB تُضبط Java قبل أي تغيير للاستضافة (القاعدة 13).
