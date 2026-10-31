# تسليم من جلسة التخطيط إلى جلسة التنفيذ

> كُتب في 2026-10-31 في نهاية جلسة التخطيط. **اقرأه أولاً** في أول جلسة تنفيذ، ثم حدّثه أو احذفه بعد إنهاء المرحلة 0.
> الخطة والقرارات في `docs/`. هذا الملف فقط عن **الحالة الآن** وما حصل في جلسة التخطيط.

## 1. عن المستخدم وطريقة العمل

- **اللغة:** يكتب بالعربي الخليجي، ويريد الشرح **بالعربي البسيط** مع المصطلحات التقنية بالإنجليزي. يستخدم Java وSpring Boot وAngular وFlyway في الدوام، وليس خبيراً في إعداد السيرفرات والاستضافة.
- **يريد أن يتحكم Claude في كل شيء ممكن**، ويطلب منه فقط ما لازم يسويه بنفسه: تسجيل الدخول للخدمات، وإنشاء الحسابات، والدفع، والموافقة على الشروط، وتغييرات إعدادات Windows.
- **لا يحب البحث في واجهات المواقع.** إذا أمكن الحصول على قيمة بأمر أو بفحص تلقائي، افعل ذلك بدل إرساله يدوّر في الصفحات (مثال: عنوان Session pooler استُخرج بتجربة الاتصال، انظر القسم 4).
- **لا مصاريف إضافية**، و**M-SAL القديم لا يتأثر بأي شكل** (القاعدتان 1 و15 في `CLAUDE.md`).
- **الأسرار:** المستخدم يضعها في `.env` بنفسه. لا تطلبها في المحادثة، ولا تطبعها (القاعدة 16).

## 2. ما اكتمل في المرحلة 0

| البند | الحالة | تفاصيل |
|---|---|---|
| Java | ✅ | Microsoft OpenJDK **21.0.12** |
| Node.js | ✅ | **v22.23.2** |
| Git | ✅ | 2.55 |
| GitHub CLI | ✅ | 2.103، ومسجّل الدخول باسم `AbdullahAltaher` (بروتوكول https) |
| WSL | ✅ | 3.0.3، بدون توزيعة Linux (`--no-distribution`) |
| Docker Desktop | ✅ | **29.9.2**، وCompose **5.6.1**، والمحرك يعمل، و`hello-world` نجح |
| مستودع GitHub | ✅ | https://github.com/AbdullahAltaher/M-SAL2.0 (**عام**، والفرع `main`) |
| مشروع Supabase | ✅ | `M-SAL2.0`، وref `bzdzveckpxmmdoqcfewv`، وSingapore، وNANO (مجاني)، وPostgres **17.11** |
| `.env` | ✅ | **كل القيم المطلوبة معبّأة** (عدا `SMTP_*` للمرحلة 10)، والاتصال بقاعدة بيانات Supabase **مُختبر ونجح** |

### إعدادات مشروع Supabase عند الإنشاء
- **Data API: معطّل** (لا API عام). وAutomatically expose new tables: معطّل تلقائياً. وAutomatic RLS: غير مفعّل (نفعّله في Flyway).
- GitHub: غير مربوط. ونوع Postgres: الافتراضي.

### توكن Supabase (`SUPABASE_ACCESS_TOKEN`)
- محصور على مشروع **M-SAL2.0** فقط، وينتهي بعد **30 يوماً** (حوالي 2026-11-30).
- الصلاحيات: **Auth Config: read-write**. و**read** فقط لـ Project Settings، وAdvisors، وLogs، وData API Config. **وكل شيء آخر None** (ومنه كل صلاحيات Database).
- **لا يقدر يقرأ البيانات ولا يغيّر الجداول.** الوصول للقاعدة بكلمة المرور فقط (Flyway وSpring Boot).
- إذا احتجت صلاحية إضافية: الصلاحيات لا تتعدل بعد الإنشاء، فاطلب من المستخدم إنشاء توكن جديد وحدد له الصلاحية بالاسم.
- **بعد إنهاء إعداد Auth، ذكّر المستخدم بحذف التوكن** (Account → Access Tokens → Revoke).

## 3. ما لم يُعمل بعد (عن قصد)

| البند | لماذا |
|---|---|
| **حساب Railway** | التجربة المجانية تبدأ من التسجيل، فيُنشأ في **المرحلة 8**. وأيضاً **التسجيل في Railway كان موقوفاً** في 2026-10-31 بعد حادثة عندهم. البدائل في `PLAN.md` |
| **إعدادات Supabase Auth** | جزء من المرحلة 0، وأنت تسويها (القسم 5) |
| **إضافة المستخدم الأول في Supabase Auth** | أنت تسويها بالتوكن أو بالـ Admin API (القسم 5)، أو المستخدم يدوياً |
| **SMTP** (Resend أو Brevo) | المرحلة 10 |
| **مستودع `M-SAL2.0-backups` الخاص** | المرحلة 10 |

## 4. ملاحظات تقنية من جلسة التخطيط (مهمة)

- **الأدوات ليست في PATH داخل Bash** (Git Bash). أضفها في بداية الأوامر:
  ```bash
  export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin:/c/Program Files/GitHub CLI"
  ```
- **Docker في Git Bash:** استخدم `MSYS_NO_PATHCONV=1` قبل `docker run`، وإلا يغيّر Git Bash المسارات.
- **عنوان Session pooler:** `aws-0-ap-southeast-1.pooler.supabase.com` (منفذ 5432). استُخرج بتجربة الاتصال بكلمة مرور وهمية: `aws-1-...` يرد "tenant not found"، و`aws-0-...` يرد "password authentication failed"، أي أنه يعرف المشروع.
- **اختبار الاتصال بدون طباعة الأسرار** (هكذا اختُبر):
  ```bash
  MSYS_NO_PATHCONV=1 docker run --rm --env-file .env postgres:17-alpine sh -c \
    'PGPASSWORD="$SUPABASE_DB_PASSWORD" psql "host=$SUPABASE_DB_HOST port=$SUPABASE_DB_PORT dbname=$SUPABASE_DB_NAME user=$SUPABASE_DB_USER sslmode=require" -tA -c "show server_version"'
  ```
- **`docker --env-file` يأخذ القيم حرفياً**، ومنها المسافات. كانت هناك مسافة قبل كلمة المرور فرفضها Supabase، وصُلّحت. إذا فشل اتصال، افحص المسافات وعلامات التنصيص **بدون طباعة القيمة**.
- **صورة `postgres:17-alpine` منزّلة** على الجهاز.
- **هوية Git في هذا المستودع فقط** (ليست global): الاسم `AbdullahAltaher`، والإيميل `184343002+AbdullahAltaher@users.noreply.github.com`، حتى لا يظهر إيميل المستخدم الحقيقي في المستودع العام.
- **نسخة مرجعية من M-SAL القديم** في `C:\Users\User\IdeaProjects\M-SAL` (للقراءة فقط، وبدون `.git`). أرقام الأسطر المذكورة في `docs/` تشير لملف `index.html` فيها (commit `c20f03e`).
- **لم تُشغَّل اختبارات M-SAL القديمة** (`node test/run-all.js`)، لأن نظام الصلاحيات منع تشغيل كود منزّل من الإنترنت في جلسة التخطيط.

## 5. أول ما تفعله جلسة التنفيذ

1. **ارفع التغييرات المعلّقة.** هذه التعديلات لم تُرفع بعد: `CLAUDE.md` (القاعدتان 15 و16)، و`docs/PLAN.md` (المنطقة، وSupabase، وRailway، والنسخ الاحتياطي)، و`docs/DATABASE.md` (Postgres 17.11 والـ pooler)، و`.env.example`، وهذا الملف. اعرضها على المستخدم واطلب موافقته على commit وpush.
2. **أكمل المرحلة 0** (`PLAN.md` القسم 14):
   - هيكل المستودع: `backend/` (Spring Boot، وJava 21، وMaven أو Gradle)، و`frontend/` (Angular)، و`docker-compose.yml` (صورة `postgres:17` محلياً).
   - تشغيل Postgres محلياً والتأكد منه.
   - **إعدادات Supabase Auth** عن طريق Management API بالتوكن (`PATCH /v1/projects/{ref}/config/auth`): قفل التسجيل المفتوح، وتفعيل Email OTP بطول 6 أرقام، وضبط Site URL وروابط localhost. التفاصيل في `API.md` القسم 2.2.
   - **إضافة المستخدم الأول** (إيميل `MSAL_BOOTSTRAP_ADMIN_EMAIL`) في Supabase Auth، مؤكَّداً وبدون كلمة مرور (`API.md` القسم 2.5). هذا يحتاج المفتاح السري عبر Auth Admin API، وليس التوكن.
3. **بعد إنهاء إعداد Auth:** ذكّر المستخدم بحذف توكن `claude-setup`.
4. **حدّث `CLAUDE.md`** (قسم التقدم) و**هذا الملف**، أو احذفه إذا لم يعد فيه شيء مفيد.
