# تسليم بين الجلسات

> آخر تحديث 2026-10-31 بعد إنهاء **المرحلة 1**. **اقرأه أولاً** في كل جلسة تنفيذ، وحدّثه في نهايتها.
> الخطة والقرارات في `docs/`. هذا الملف فقط عن **الحالة الآن** والملاحظات التي لا تظهر في الكود.

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
| هيكل `backend/` | ✅ | Spring Boot **4.1.2**، وJava 21، وMaven wrapper (`./mvnw`، لا يحتاج تثبيت Maven). فيه web وactuator وvalidation وjdbc وpostgresql فقط. `./mvnw verify` نجح |
| هيكل `frontend/` | ✅ | Angular **22.2.5**، وSCSS، وrouting، وبدون SSR، وVitest. صفحة مؤقتة عربية RTL. `ng test` و`ng build` نجحا |
| `docker-compose.yml` | ✅ | المشروع `msal2`: `db` (`postgres:17-alpine`، والمنفذ **5433** على الجهاز، وتوقيت Asia/Dubai) و`backend` (صورة `m-sal2.0`، و`mem_limit: 512m`). `/actuator/health` = UP، والذاكرة **حوالي 160MB من 512MB** |
| مفاتيح JWT في Supabase | ✅ | **ES256** (JWKS)، فلا سر مشترك. سُجّل في `API.md` 2.6 |
| المستخدم الأول في Supabase Auth | ✅ | أُنشئ 2026-10-31 بالمفتاح السري (Admin API): إيميل `MSAL_BOOTSTRAP_ADMIN_EMAIL`، ومؤكَّد، وبدون كلمة مرور. id `d2571c54-64d6-4355-b8ba-81f126c8e28e` |
| إعدادات Supabase Auth | ✅ | طُبّقت 2026-10-31 وتأكدنا منها بالقراءة: `disable_signup=true`، والرمز 6 أرقام وصالح 600 ثانية، وSite URL وروابط localhost:4200 وGitHub Pages. **القالب لم يُطبّق** (انظر القسم 3) |

### إعدادات مشروع Supabase عند الإنشاء
- **Data API: معطّل** (لا API عام). وAutomatically expose new tables: معطّل تلقائياً. وAutomatic RLS: غير مفعّل (نفعّله في Flyway).
- GitHub: غير مربوط. ونوع Postgres: الافتراضي.

### توكن Supabase (`SUPABASE_ACCESS_TOKEN`)
- **التوكنات تُحذف بعد كل استخدام.** `claude-setup` (Auth Config: read-write) **لم يكفِ** لـ `PATCH /config/auth`، فهو يطلب `project_admin_write`. ثم أُنشئ `claude-setup-2` بصلاحيتين: **Project Settings: Read and write** و**Auth Config: Read and write**، ومحصور على M-SAL2.0، وينتهي بعد 7 أيام. هذا التركيب **نجح**.
- **عند الحاجة لتوكن جديد** (مثل قالب الإيميل في المرحلة 10): اطلب من المستخدم إنشاءه من https://supabase.com/dashboard/account/tokens بنفس الصلاحيتين، ووضعه في `.env`، وحذفه بعد الانتهاء.
- التوكن **لا يقدر يقرأ البيانات ولا يغيّر الجداول.** الوصول للقاعدة بكلمة المرور فقط (Flyway وSpring Boot).

## 2ب. ما اكتمل في المرحلة 1 (أساس السيرفر)

| البند | تفاصيل |
|---|---|
| Flyway | V1–V6 منسوخة كما هي من `DATABASE.md`، وتعمل على Postgres 17 (Testcontainers وDocker). Hibernate بوضع `validate` فقط |
| JWT | `system/security/SecurityConfig`: JWKS بـ ES256، و`iss` و`aud = authenticated`. توكن مزوّر بمفتاح آخر يُرفض بـ `SYS-E001` (جُرّب على الحاوية مع Supabase الحقيقي) |
| `@ServiceCode` والصلاحيات | `system/access/ServiceAccessInterceptor`: بالترتيب في `API.md` القسم 1. Controller بدون `@ServiceCode` يُرفض، واختبار يتأكد أن كل Controller عليه كود موجود في `services`. المسارات تحت `/api/v1` تلقائياً |
| المستخدم الأول والدعوات | `users/UserProvisioningService`: دعوة PENDING غير منتهية، أو المدير الأول عندما لا يوجد مستخدمون. غير ذلك `SRV-000-E001` |
| السجلات | `system/logging/RequestLoggingFilter`: `X-Request-Id` لكل طلب، وسطر واحد لكل طلب API بالكود والمستخدم والمدة فقط |
| `audit_log` | `system/audit/AuditService`: يأخذ كود الخدمة ورقم الطلب من MDC، ويعمل داخل نفس الـ transaction (`MANDATORY`) |
| الأخطاء | `system/error`: `ErrorCode` (SYS وSRV-000 فقط الآن، تُضاف أكواد كل خدمة معها)، والشكل في `API.md` 3.1. رسالة Bean Validation تقبل كود خطأ (`@Pattern(message = "SRV-001-E001")`) |
| JSON | `BigDecimal` يُكتب نصاً (`"46.25"`) |
| SRV-000 | `GET /me` و`PATCH /me` كاملان عدا `dailyReminderTime` (المرحلة 7) |
| الاختبارات | **32 اختباراً** (`./mvnw verify`، تحتاج Docker): الـ migrations وRLS وFK المركّب، والـ JWT، وكل أكواد الصلاحيات، و`/me`، وعزل البيانات، والتذكير بالتصدير (R-IO-01) |
| الذاكرة (القاعدة 13) | بعد التشغيل **267MB**، وأعلى قيمة تحت 4,000 طلب **294MB من 512MB** (57%)، بدون OOM |

## 3. ما لم يُعمل بعد (عن قصد)

| البند | لماذا |
|---|---|
| **الاستضافة (Render)** | **الحساب أُنشئ** (Hobby مجاني)، ومشروع فارغ `M-SAL2.0` ببيئة `Production`. **الخدمة تُنشأ في المرحلة 8** فقط. **Railway مستبعد** بعد حادثة أمنية عندهم (التسجيل موقوف بلا موعد). المقارنة والقرار في `PLAN.md` ("دراسة الاستضافة") |
| **قالب إيميل الرمز** | الخطة المجانية ترفض تعديل القالب مع مزود الإيميل الافتراضي: `Email template modification is not available for free tier projects using the default email provider`. القالب جاهز في `docs/supabase/otp-email.html`، ويُطبّق مع SMTP |
| **الميزانية الافتراضية "الأساسي" عند أول دخول** | مع SRV-002 في **المرحلة 3** (R-BUD-06). مكانها معلَّم بتعليق في `UserProvisioningService.provision` |
| **تحديد الطلبات** (120 في الدقيقة، `SYS-E007`)، وحدود حجم الطلب، وspringdoc (Swagger في التطوير فقط) | من `API.md` القسم 1 و7، ولم تكن في نطاق المرحلة 1. الأنسب في **المرحلة 3** مع الـ endpoints |
| **قاعدة بيانات Supabase ما زالت فارغة** | الـ migrations جُرّبت محلياً فقط. تُطبَّق على Supabase عند أول تشغيل للسيرفر بإعدادات Supabase (المرحلة 8، أو قبلها بموافقة المستخدم) |
| **تجربة توكن Supabase حقيقي من البداية للنهاية** | تحتاج تسجيل دخول برمز (المرحلة 4). الحاوية تجلب JWKS من Supabase وترفض المزوّر، والاختبارات تغطي ما بعد التحقق |
| **SMTP** (Resend أو Brevo) | المرحلة 10. **انتبه:** بدونه، إيميل الدخول فيه **رابط وليس رمزاً** (القالب الافتراضي)، فلا يقدر المستخدم يكتب الرمز في التطبيق. في المرحلة 4، إما تقديم SMTP والقالب (مجاني، ويحتاج موافقة المستخدم على التوقيت)، أو للتجربة المحلية: `POST /auth/v1/admin/generate_link` بـ `type: magiclink` يرجع `email_otp` يُستخدم مع `verifyOtp` |
| **مستودع `M-SAL2.0-backups` الخاص** | المرحلة 10 |

## 4. ملاحظات تقنية (مهمة)

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
- **قراءة `.env` من سكربت بدون طباعة الأسرار:** سكربت Node صغير يقرأ الملف ويستخدم القيم في `fetch` ويطبع الحالة فقط (هكذا طُبّقت إعدادات Auth وأُضيف المستخدم الأول). الإعدادات ترجع بـ `GET /v1/projects/{ref}/config/auth`.
- **نظام الصلاحيات في Claude Code** يمنع تغيير إعدادات الخدمات بدون موافقة المستخدم في المحادثة، فاطلبها أولاً.
- **سطر السجل الفارغ** عند بداية التشغيل يظهر `[] [user=] [req=]`، وهذا طبيعي (لا يوجد طلب).
- **تحذيرات Maven `401`** من مستودعات Red Gate وflyway-community-db-support تأتي من pom الخاص بـ Flyway، ولا تؤثر.
- **المنفذ 5432 مشغول** بـ Postgres مثبّت على Windows (ليس لنا، لا تلمسه). قاعدة Docker على **5433** (`LOCAL_DB_PORT`)، وقيمة `DB_PORT` الافتراضية في `application.yml` هي 5433 للتشغيل من خارج Docker.
- **إعدادات Auth قبل التعديل** (للرجوع إليها): `disable_signup=false`، و`mailer_otp_length=8`، و`mailer_otp_exp=3600`، و`site_url=http://localhost:3000`، و`uri_allow_list` فارغ، وقالب Magic link الافتراضي (رابط بدون رمز). SMTP غير مضبوط.
- **هوية Git في هذا المستودع فقط** (ليست global): الاسم `AbdullahAltaher`، والإيميل `184343002+AbdullahAltaher@users.noreply.github.com`، حتى لا يظهر إيميل المستخدم الحقيقي في المستودع العام.
- **نسخة مرجعية من M-SAL القديم** في `C:\Users\User\IdeaProjects\M-SAL` (للقراءة فقط، وبدون `.git`). أرقام الأسطر المذكورة في `docs/` تشير لملف `index.html` فيها (commit `c20f03e`).
- **لم تُشغَّل اختبارات M-SAL القديمة** (`node test/run-all.js`)، لأن نظام الصلاحيات منع تشغيل كود منزّل من الإنترنت في جلسة التخطيط.

## 5. الخطوة التالية: المرحلة 2 (المنطق)

حسب `PLAN.md` القسم 14: نقل الحسابات إلى Java حسب `BUSINESS_RULES.md`، و**كل قاعدة لها اختبار JUnit بنفس أرقام M-SAL القديم** (القاعدة 10)، وتحويل اختبارات M-SAL (`test/*.test.js` في النسخة المرجعية) إلى JUnit. "اليوم" من `Clock` (موجود في `AppConfig`).
