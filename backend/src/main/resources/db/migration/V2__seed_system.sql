-- V2__seed_system.sql: from docs/DATABASE.md section 7

insert into services (code, name_ar, name_en) values
    ('SRV-000', 'الحساب والجلسة',             'Account & session'),
    ('SRV-001', 'المصاريف',                   'Expenses'),
    ('SRV-002', 'الميزانية الشهرية',          'Monthly budget'),
    ('SRV-003', 'دورات الراتب',               'Salary cycles'),
    ('SRV-004', 'الحسابات',                   'Accounts'),
    ('SRV-005', 'الفئات',                     'Categories'),
    ('SRV-006', 'الإحصاءات',                  'Statistics'),
    ('SRV-007', 'الاستيراد والتصدير',         'Import & export'),
    ('SRV-008', 'الإيصال',                    'Receipt'),
    ('SRV-009', 'إدارة المستخدمين والدعوات',  'Users & invitations');

insert into roles (code, name_ar) values
    ('USER',  'مستخدم'),
    ('ADMIN', 'مدير');

insert into role_services (role_code, service_code, can_read, can_write) values
    ('USER',  'SRV-000', true, true),
    ('USER',  'SRV-001', true, true),
    ('USER',  'SRV-002', true, true),
    ('USER',  'SRV-003', true, true),
    ('USER',  'SRV-004', true, true),
    ('USER',  'SRV-005', true, true),
    ('USER',  'SRV-006', true, false),
    ('USER',  'SRV-007', true, true),
    ('USER',  'SRV-008', true, false),
    ('ADMIN', 'SRV-009', true, true);
