# تسليم بين الجلسات

> **اقرأه أولاً، وحدّثه في نهاية كل جلسة.** مختصر عمداً (الحالة الآن وما لا يظهر في الكود).
> التفاصيل التاريخية لكل مرحلة في [HANDOFF-HISTORY.md](HANDOFF-HISTORY.md): ابحث فيه بـ `grep` عند الحاجة، ولا تقرأه كاملاً.
> آخر تحديث: 2026-11-07.

## 1. المالك وطريقة العمل

- **العربي الخليجي**، والشرح بالعربي البسيط والمصطلحات التقنية بالإنجليزي. يعرف Java وSpring وAngular وFlyway، وليس خبيراً في السيرفرات.
- **يريد أن يتحكم Claude في كل شيء ممكن.** بيده فقط: تسجيل الدخول للخدمات، وإنشاء الحسابات، والدفع، والموافقة على الشروط، وإعدادات Windows. لا يحب البحث في واجهات المواقع: استخرج القيم بأوامر.
- **commit وpush فقط بطلبه** (يوافق عادة: "نعم، commit push"). `main` ينشر التطبيق مباشرة.
- **أي تصميم جديد:** نقاش وأسئلة ونماذج مرئية قبل أي كود، والنماذج تشرح الفكرة لا الشكل النهائي.
- **الأسرار** في `.env` يضعها بنفسه: لا تطلبها ولا تطبعها (القاعدة 16). **المستودع عام:** لا أرقام ولا أسماء من بياناته الحقيقية في `docs/` أو الاختبارات.

## 2. الحالة الآن

- **المراحل 0 إلى 10 مكتملة**، و**التصميم الجديد** (`DESIGN.md`، ت1 إلى ت7) **مدموج في `main` ومنشور** (`63b0879`)، وجرّبه المالك على الآيفون وصلّحنا ثلاث ملاحظات (شريط الأيام، والمسافة تحت "وصل الراتب"، والوصف الإنجليزي). فرع `redesign` مدموج ويمكن حذفه.
- **التالي: المرحلة 11 (دعوة الأهل والأصدقاء).** قرار معلّق عند المالك: كيف تصل الدعوة:
  1. **زر "مشاركة الدعوة"** في S-30 (`navigator.share` ونسخ احتياطي) برسالة جاهزة: الرابط، وخطوات التثبيت، و"ادخل بإيميلك واكتب الرمز". بدون أسرار جديدة. **(الاقتراح)**
  2. إيميل دعوة تلقائي عبر `POST /auth/v1/invite` مع قالب عربي: يحتاج توكن Supabase وعملاً أكثر.
  - الآن: الدعوة تُنشئ مستخدم Supabase بدون إيميل (`SupabaseAuthAdmin.createUser`، جُرّب حقيقياً). بعد القرار: شخص واحد أولاً، ثم الباقون.
- **لم يُجرَّب على الآيفون بعد:** صورة البطاقة من الألبوم، وشاشة القفل الجديدة مع Face ID، وصورة بطاقة الحساب في المصاريف.
- **اقتراحات لاحقة (غير ضرورية):** تنبيهات Dependabot، وRLS على `flyway_schema_history` بـ `V9`، ومسح أرقام بياناته القديمة من تاريخ git (يحتاج `push --force`: قرار المالك).

## 3. أين كل شيء

| الشيء | أين |
|---|---|
| التطبيق | https://abdullahaltaher.github.io/M-SAL2.0/ (GitHub Pages، `pages.yml` عند push على `main` في `frontend/**`) |
| السيرفر | https://m-sal2.onrender.com (Render Starter، Singapore، `render.yaml`، ينشر بعد نجاح CI على `main`) |
| قاعدة البيانات والدخول | Supabase مشروع `M-SAL2.0` (ref `bzdzveckpxmmdoqcfewv`)، Session pooler `aws-0-ap-southeast-1.pooler.supabase.com:5432` |
| الإيميلات (رمز الدخول) | Gmail المالك بـ App Password عبر `scripts/apply-auth-email.mjs`، والقالب `docs/supabase/otp-email.html` |
| النسخ الاحتياطي | مستودع خاص `M-SAL2.0-backups`، يومياً 03:30 دبي، التفاصيل في [BACKUP.md](BACKUP.md). القالب `ops/backup/` (ليس `backups/`: مُتجاهل في git) |
| CI | `ci.yml` على `main` وطلبات الدمج فقط (ليس على فرع مرفوع) |
| M-SAL القديم | يبقى يعمل احتياطاً، لا يُلمس. نسخة للقراءة في `C:\Users\User\IdeaProjects\M-SAL` |

**المراجع:** القواعد والاختبارات `BUSINESS_RULES.md` (القسم 11 = أين يُختبر كل قاعدة)، والعقد `API.md` (القسم 9 = قرارات التنفيذ)، والشاشات `SCREENS.md` (أوله: فروق التصميم الجديد)، والشكل `DESIGN.md` (38 قراراً، `D-xx`).

## 4. التشغيل المحلي والتجربة

- **Git Bash:** الأدوات ليست في PATH: `export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin:/c/Program Files/GitHub CLI"`، و`MSYS_NO_PATHCONV=1` قبل `docker run`. **لا Python على الجهاز:** استخدم Node للسكربتات.
- **Docker Desktop** يُشغَّل بـ `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`، ثم `docker compose up -d` (قاعدة على **5433**، والسيرفر على **8080**). المنفذ 5432 لـ Postgres آخر على Windows: لا تلمسه.
- **الاختبارات:** `backend/./mvnw test` (191، تحتاج Docker)، و`frontend/npx ng test --watch=false` (68، Vitest)، و`npm run build` (الحزمة الأولى ~447KB، الحد 500KB).
- **الواجهة في المتصفح المدمج:** `preview_start` باسم `frontend` (`.claude/launch.json`)، ومقاس `mobile`. إذا كان المتصفح مخفياً لا يرسم إطارات: اعرضه بـ `show_pane preview`.
- **الدخول المحلي الحقيقي:** المالك يدخل بإيميله (الرمز يصل على Gmail)، أو `node scripts/dev-sign-in-link.mjs` **يشغّله المالك بنفسه**.
- **التجربة بدون دخول (الأسرع، استُخدمت في كل التصميم):** في `/login`، سكربت في الصفحة يستبدل `window.fetch` لعناوين `http://localhost:8080/api/v1` ببيانات وهمية (Angular يستخدم **fetch** وليس XHR)، ويضع جلسة وهمية: `const p = ng.getComponent(document.querySelector('app-login-page'))`، ثم `p.auth.accessToken = async () => 'x'`، و`p.auth.whenReady = async () => {}`، و`p.auth.signOut = async () => {}`، و`p.auth.session.set({access_token:'x', user:{email:'x@example.test'}})`، ثم `p.router.navigateByUrl('/expenses/home')`. لا يلمس السيرفر ولا Supabase، ويضيع مع أي إعادة تحميل (وتعديل ملف TS يعيد التحميل).
- **السكربتات الطويلة:** لا تضعها داخل `node -e "..."` أو heredoc فيها backticks (Bash يكسرها). اكتبها في ملف داخل scratchpad وشغّلها بـ `node <file>`.

## 5. فخاخ (مهمة)

- **`view-transition-name` يقطع الضبابية:** البطاقات داخله لا ترى الخلفية. الاسم `page` على `.view` فقط أثناء الحركة (`html[data-motion]`).
- **الانتقالات:** `skipInitialTransition` لازم، والتغيير بلا حركة يُتخطى (`page-motion.ts`)، وإلا تعلق الصفحة إذا لم تُرسم إطارات. Angular يطبع "Transition was skipped" في التطوير فقط.
- **الآيفون و`backdrop-filter`:** لا تكبّر عنصراً فوق بطاقة زجاجية بحركة طي (`Collapse`): يبقى رسم البطاقة القديم فوقه حتى يُعاد فتح التطبيق. أظهره مباشرة (`@if`).
- **أسماء كلاسات عامة:** `sheet`، و`row`، و`panel`، و`field`... مستخدمة في `ledger.css` و`glass.css`. لا تستخدمها لعناصر داخلية بمعنى آخر.
- **`.g-hero` فيها `overflow:hidden`:** القوائم المنسدلة منها توضع خارجها.
- **Spring:** استدعاء `@Transactional` من service أخرى ثم التقاط الاستثناء يجعل الـ transaction rollback-only (لذلك `CategoryService.find` يرجع `Optional`).
- **Guards:** `inject()` لا يعمل بعد `await`، فكل guard يأخذ ما يحتاجه أولاً.
- **CSP في `index.html`:** أي عنوان خارجي جديد (API أو خط) يُضاف لها وإلا يُمنع. الصور المحلية `blob:` و`data:` مسموحة.
- **التخزين في الجهاز:** المفاتيح تبدأ بـ `msal2.` (`msal2.auth`، و`msal2.theme`، و`msal2.tab`، و`msal2.budget.folded`، و`msal2.lock`، و`msal2.install.skipped`)، والصور في IndexedDB `msal2.photos`. **ممنوع أي `mizaniya`.**
- **`docker --env-file`** يأخذ القيم حرفياً (مسافة قبل كلمة مرور أفشلت الاتصال مرة).
- **`backend/mvnw`** يجب أن يبقى `100755` في git، وإلا يفشل البناء على Linux.

## 6. الأسرار والتوكنات

- **توكن Supabase** (`SUPABASE_ACCESS_TOKEN`) عند الحاجة فقط (مثل إعدادات Auth): يُنشئه المالك من https://supabase.com/dashboard/account/tokens بصلاحيتي **Project Settings: Read and write** و**Auth Config: Read and write**، محصوراً على M-SAL2.0، ويضعه في `.env`، و**يُحذف بعد الاستخدام**. لا يقدر يقرأ البيانات ولا يغيّر الجداول.
- **مفاتيح VAPID** أنشأها المالك ولا تُنشأ مرة ثانية (تغييرها يلغي اشتراك كل الأجهزة). `VAPID_SUBJECT` = عنوان التطبيق (عنوان وهمي جعل Apple يرفض بـ `BadJwtToken`).
- **إذا تغيّرت كلمة مرور Google أو أُلغي App Password:** تتوقف رموز الدخول. الحل: App Password جديد في `.env`، ثم `scripts/apply-auth-email.mjs` (يحتاج توكن Supabase).
- **ذاكرة Render:** حوالي 290 إلى 355MB من 512MB. إذا اقتربت من 450MB تُضبط إعدادات Java قبل أي تغيير للاستضافة (القاعدة 13).
