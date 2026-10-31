-- V5__seed_category_templates.sql: from docs/DATABASE.md section 7

insert into category_templates (code, name_ar, emoji, sort_order, default_suggestions) values
    ('cat_food',        'أكل ومطاعم',       '🍔',  1, '{ريوق,غدا,عشا}'),
    ('cat_grocery',     'بقالة',            '🛒',  2, '{}'),
    ('cat_fuel',        'بنزين ومواصلات',   '⛽',  3, '{}'),
    ('cat_bills',       'فواتير',           '🧾',  4, '{}'),
    ('cat_electricity', 'كهرباء',           '⚡',  5, '{}'),
    ('cat_water',       'ماء',              '💧',  6, '{}'),
    ('cat_telecom',     'اتصالات وإنترنت',  '📱',  7, '{}'),
    ('cat_rent',        'إيجار',            '🏠',  8, '{}'),
    ('cat_shopping',    'ملابس وتسوق',      '👕',  9, '{}'),
    ('cat_health',      'صحة',              '💊', 10, '{}'),
    ('cat_fun',         'ترفيه',            '🎮', 11, '{}'),
    ('cat_coffee',      'قهوة وكافيهات',    '☕', 12, '{}'),
    ('cat_travel',      'سفر',              '✈️', 13, '{}'),
    ('cat_edu',         'تعليم',            '🎓', 14, '{}'),
    ('cat_kids',        'أطفال',            '🧸', 15, '{}'),
    ('cat_gifts',       'هدايا',            '🎁', 16, '{}'),
    ('cat_charity',     'تبرعات وصدقة',     '🤝', 17, '{}'),
    ('cat_care',        'عناية شخصية',      '🧼', 18, '{}'),
    ('cat_pets',        'حيوانات أليفة',    '🐕', 19, '{}'),
    ('cat_other',       'أخرى',             '📦', 20, '{}');
