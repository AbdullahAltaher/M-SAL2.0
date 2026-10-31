# تسليم بين الجلسات

> آخر تحديث 2026-11-01 بعد إنهاء **المرحلة 6**. **اقرأه أولاً** في كل جلسة تنفيذ، وحدّثه في نهايتها.
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

## 2ج. ما اكتمل في المرحلة 2 (المنطق)

- **حسابات بدون قاعدة بيانات** في كل وحدة: `cycles/Cycles` و`CycleSummary`، و`expenses/EntryDays` و`EntrySearch` و`NameSuggestions` و`EntryRules`، و`stats/Stats` و`CategoryTotals`، و`budget/BudgetTotals` و`MonthLabel`، و`accounts/AccountRules`، و`receipt/ReceiptModel`. ومشتركة: `system/money/Money` و`system/time/Today`.
- **المدخلات records بسيطة** (`EntryData`، و`CycleData`، و`AccountData`). في المرحلة 3 تبني الـ Services هذه الـ records من قاعدة البيانات وتمررها للحسابات، والحسابات لا تعرف JPA.
- **`Money.round` يطابق `Math.round`**: النصف للأعلى في الموجب، ونحو الصفر في السالب. مسجّل في R-GEN-01.
- **103 اختبارات** تنجح (`./mvnw verify`)، منها 71 اختبار حسابات بنفس أرقام M-SAL القديم. **خريطة كل قاعدة واختبارها** في `BUSINESS_RULES.md` القسم 11، وفيها ما ينتظر المرحلة 3 وAngular والمرحلة 6.
- **المحذوف** يحمله `EntryData.deletedAt`، والحسابات تستبعده بنفسها، فلا يعتمد الصحيح على أن الاستعلام فلتره.

## 2د. ما اكتمل في المرحلة 3 (الـ API)

- **Endpoints** SRV-000 إلى SRV-006، وSRV-008، وSRV-009 كما في `API.md` القسم 4، وما تقرر أثناء التنفيذ في `API.md` **القسم 9** (مهم لـ Angular).
- **Entities** بدون علاقات JPA (أعمدة UUID فقط)، وكل Repository فيه `findOwned` يرجع السجل فقط إذا كان للمستخدم وميزانيته غير محذوفة.
- **أول دخول** يُنشئ ميزانية "الأساسي" كاملة (`BudgetService.createWithDefaults`).
- **`V7__add_invitation_auth_user.sql`**: عمود `invitations.auth_user_id`، فصار ملف الإشعارات `V8` (`DATABASE.md`).
- **`SupabaseAuthAdmin`** يستدعي Auth Admin API بالمفتاح السري (`SUPABASE_SECRET_KEY`). **لم يُجرَّب مع Supabase الحقيقي بعد**، والاختبارات تستخدم mock. أول دعوة حقيقية ستكون التجربة.
- **140 اختباراً** تنجح، منها `IsolationTest` (38 طلباً من مستخدم على بيانات غيره ← 404، وبياناته لم تتغير).
- **الذاكرة** بنفس حدود Render (`mem_limit: 512m` و`cpus: 0.5` في compose): بعد التشغيل **293MB**، وأعلى قيمة تحت 4,000 طلب **320MB من 512MB** (63%). بنصف CPU تختار Java بنفسها SerialGC. **بدون حد CPU** (G1) وصلت 402MB، فحد الـ CPU في compose مهم ليكون القياس واقعياً.
- **فخ مهم في Spring:** استدعاء method عليها `@Transactional` من service أخرى ثم التقاط الاستثناء داخل transaction مفتوحة يجعلها rollback-only. لهذا `CategoryService.find` يرجع `Optional` بدل الاستثناء.

## 2هـ. ما اكتمل في المرحلة 4 (أساس Angular)

- **الهيكل** كما في `SCREENS.md` القسم 6: `core/` (auth، وapi، وsession، وpermissions، وutil)، و`features/` (auth، وshell)، و`shared/` (فارغ الآن). Standalone وsignals وتحميل كسول.
- **الإعداد بدون أسرار في git:** `frontend/scripts/write-env.mjs` يقرأ `../.env` ويكتب `src/environments/environment.generated.ts` (في `.gitignore`) قبل `npm start` و`build` و`test`. ولا يطبع القيم. في المرحلة 8 تأتي القيم من متغيرات GitHub Actions.
- **الدخول (S-01):** `core/auth/supabase-auth.ts` (supabase-js 2.118.0، للدخول فقط، `shouldCreateUser: false`). الجلسة في `localStorage` تحت `msal2.auth`. خطوتان: الإيميل ثم الرمز (6 أرقام، و`autocomplete="one-time-code"`، وإعادة الإرسال بعد 60 ثانية). **جُرّب مع Supabase الحقيقي:** إيميل غير مدعو ← "هذا البريد غير مدعو لاستخدام التطبيق."، و**المستخدم جرّب الدخول الحقيقي بإيميله (2026-11-01)**.
- **حتى يأتي SMTP (المرحلة 10):** الإيميل فيه **رابط** وليس رمزاً. الرابط يرجع لـ `emailRedirectTo` (عنوان التطبيق) و`detectSessionInUrl` يُكمل الدخول. هذا يعمل على الكمبيوتر الآن، لكنه **لا يناسب تطبيق الآيفون المثبّت**.
- **الصلاحيات:** `authGuard` و`guestGuard` و`serviceGuard(SRV-xxx)` و`*appIfService`. **فخ:** الـ guards على نفس المسار تعمل معاً، و`inject()` لا يعمل بعد `await` (NG0203)، فكل guard يأخذ ما يحتاجه أولاً. `guards.spec.ts` يحمي من رجوع هذا الخطأ.
- **التصميم:** `src/styles/tokens-ledger.css` و`tokens-expenses.css` منسوخة **كما هي** من M-SAL (الأسطر 20–52 و349–381)، و`base.css` (الأسطر 53–68). قسم المصاريف يضيف `body.x-on` مثل القديم. خط IBM Plex Sans Arabic.
- **`core/util`:** تحليل المبالغ (R-ENT-03، وR-BUD-03، وR-GEN-04)، والتواريخ (R-GEN-06)، وتنسيق المبالغ (R-GEN-05)، ومجاميع الميزانية (R-BUD-01)، **مع اختبارات القديم نفسها** (`util.spec.ts`).
- **الشاشات** في المسارات (`/budget`، و`/expenses/*`، و`/admin`) صفحة مؤقتة `PlaceholderPage` تُستبدل في المرحلة 5.
- **24 اختبار Angular** و`ng build` ينجحان. أساس `/M-SAL2.0/` في بناء الإنتاج.
- **`.claude/launch.json`**: إعداد `frontend` لتشغيل `npm start` في Browser pane.

## 3. ما لم يُعمل بعد (عن قصد)

| البند | لماذا |
|---|---|
| **الاستضافة (Render)** | **الحساب أُنشئ** (Hobby مجاني)، ومشروع فارغ `M-SAL2.0` ببيئة `Production`. **الخدمة تُنشأ في المرحلة 8** فقط. **Railway مستبعد** بعد حادثة أمنية عندهم (التسجيل موقوف بلا موعد). المقارنة والقرار في `PLAN.md` ("دراسة الاستضافة") |
| **قالب إيميل الرمز** | الخطة المجانية ترفض تعديل القالب مع مزود الإيميل الافتراضي: `Email template modification is not available for free tier projects using the default email provider`. القالب جاهز في `docs/supabase/otp-email.html`، ويُطبّق مع SMTP |
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


## 5. المرحلة 5 (الشاشات)

**مكتمل (2026-11-01):**
- CSS القديم منسوخ كما هو: `src/styles/ledger.css` (الأسطر 69–348) و`expenses.css` (382–710)، والقوالب بنفس أسماء الـ classes وبنية الـ HTML. عناصر الشاشات (`app-home-view` وغيرها) `display: contents` حتى تبقى داخل عمود `.view` مثل القديم (`src/styles.scss`).
- مشترك: `core/api/api.ts` و`models.ts`، و`core/data/budgets-store.ts`، و`shared/` (Icon، وMoneyView، وSheets/SheetHost مع السحب، وToasts، وConfirm/Prompt بدل `confirm()`/`prompt()`، و`Collapse`).
- المصاريف (S-20، S-21، SH-01 إلى SH-07)، والإحصاءات (S-22)، والإيصالان (canvas منقول من القديم)، والميزانية (S-10) مع `LedgerStore` (حفظ تلقائي 600ms، وطلب واحد لكل سجل بآخر version)، والهيدر بقائمة الأشخاص، والإدارة (S-30)، والإعدادات (SH-08: الحساب والخروج فقط).
- **إصلاح مهم:** `provideAppInitializer` في `app.config.ts` ينتظر supabase-js قبل أول تنقل، وإلا يمسح أول تحويل رموز الجلسة من رابط الدخول.
- **32 اختبار Angular** و`ng build` ينجحان.

**جُرّب في المتصفح المدمج ببيانات حقيقية (مقاس آيفون، Chrome وليس الآيفون):** وصل الراتب بأرقام عربية، و"تقدر تصرف اليوم" (100 ← 98 ← 51.75)، ونافذة المصروف (الأرقام العربية، وطي الشبكة، والاقتراحات، و"اكتب وصف"، وEnter يحفظ)، والقائمة والبحث ("صدقه" يجد "صدقة")، والإحصاءات (النسب 100%)، والميزانية (المجاميع فوراً، و"~"، و"محفوظ ✓"، والحفظ بعد إعادة التحميل)، و"المزيد"، والإيصال، والإدارة.

**لم يُجرَّب بعد:** على الآيفون نفسه (لوحة المفاتيح مع انزلاق النافذة، والسحب، والنوتش)، والوضع الفاتح، والحسابات المتعددة والسحب بينها، والفئات والمحذوفات وتعديل الدورة، و"شهر جديد".

**ملاحظات:**
- **الدخول للتجربة بدون إيميل:** `node scripts/dev-sign-in-link.mjs` يطبع رابط دخول لمرة واحدة (Admin API بالمفتاح السري)، لأن إيميل Supabase الافتراضي يسمح بإيميلين فقط في الساعة. **المستخدم يشغّله بنفسه** ويفتح الرابط في المتصفح المدمج. احذفه أو أوقفه مع SMTP (المرحلة 10).
- **حجم الحزمة الأولى 543KB** (تحذير فوق 500KB، والحد 1MB). يُراجع في المرحلة 7.

## 5ب. المرحلة 6 (الاستيراد والتصدير)

**مكتمل (2026-11-01):** الجلسة الأولى للمرحلة علّقت بعد كتابة أغلب السيرفر بدون commit، وأكملتها جلسة ثانية.
- **السيرفر** في `importer/`: `ImportParser` (sanitize القديم بدون حفظ، والأعداد مثل countState، والتحذيرات في `Warnings`)، و`ImportService` (الدمج والاستبدال في transaction واحدة، مع `audit_log.before_data` = ملف تصدير كامل قبل الاستيراد، و`imports.raw_data` = الملف الأصلي)، و`ExportService` (صيغة القديم `schemaVersion: 3`)، و`LedgerAmounts` (`"~15,000"` مثل parseLedgerAmount في Angular). JDBC مباشرة وليس JPA، لأن الاستيراد يكتب سجلات كثيرة مرة واحدة.
- **المطابقة:** `id` السجل، ثم `legacy_id` في نفس المكان، والفئة الافتراضية بالـ `code` أيضاً. والـ UUID من الملف يصير id السجل إذا لم يكن مأخوذاً (حتى عند مستخدم آخر)، وإلا id جديد مع `legacy_id`. القرارات في `API.md` 9.4.
- **أُصلح أثناء الإكمال:** التصدير ثم الاستيراد بالاستبدال كان يمسح `code` الفئات الافتراضية، فصار التصدير يكتب `code`.
- **Angular:** `features/settings/import-export.ts` (التصدير، والمشاركة، واختيار الملف، والمعاينة، ثم تحديث كل الشاشات)، و`import-sheet.ts` (SH-09 بنصوص القديم)، وزرّا "تصدير" و"استيراد" في الميزانية، وقسم "البيانات" في SH-08 مع سجل الاستيراد، وشريط تذكير التصدير (Banner) تحت الهيدر.
- **الاختبارات:** **169 اختبار JUnit** (منها `ImportParserTest` 19 و`ImportApiTest` 10: الدمج مرتين بدون تكرار، والدورة بنفس التاريخ، والاستبدال يرجع نفس التصدير، والاستبدال بدون قسم مصاريف، وملف شخص واحد، والعزل، وأكواد الأخطاء)، و**38 اختبار Angular**، و`ng build` ينجح.

**جُرّب بملف حقيقي (2026-11-01):** المستخدم صدّر ملفه من M-SAL القديم على الآيفون واستورده محلياً (Docker) بـ `REPLACE`. بدون تحذيرات، والأعداد مطابقة للملف (3 مجموعات، و11 بنداً، وحساب، ودورتان، و114 مصروفاً، وفئتان خاصتان)، ومجموع المبالغ في القاعدة **7,728.81** مثل الملف بالضبط، والتواريخ من 2026-09-25 إلى 2026-11-01. والميزانية القديمة حُذفت مؤقتاً، و`audit_log.before_data` محفوظ. الانتقال الفعلي يبقى في المرحلة 9 على Supabase.

**لم يُجرَّب بعد:** "مشاركة الملف" على الآيفون (`navigator.share` بعد طلب الشبكة قد يرفضه Safari لأن الضغطة "انتهت"، وعندها يُنزَّل الملف).

## 6. الخطوة التالية: المرحلة 7 (ميزات PWA)

حسب `PLAN.md` القسم 14: الأيقونة، وشاشة البداية، والإشعارات (مع `V8` والمهمة المجدولة)، وFace ID، والتحديثات، وصفحة التثبيت. وتُراجع فيها حجم الحزمة الأولى (543KB).
