const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/routes/_layout.tsx';
let content = fs.readFileSync(file, 'utf8');

if (!content.includes('/browser-settings')) {
  content = content.replace(
    /\{ path: '\/security-center', label: 'navigation\.securityCenter', icon: KeyRound, group: 'system', roles: \['SUPER_ADMIN', 'ADMIN', 'SECURITY'\] \},/,
    "{ path: '/security-center', label: 'navigation.securityCenter', icon: KeyRound, group: 'system', roles: ['SUPER_ADMIN', 'ADMIN', 'SECURITY'] },\n  { path: '/browser-settings', label: 'navigation.browserSettings', icon: Settings, group: 'system', roles: ['SUPER_ADMIN', 'ADMIN'] },"
  );
  fs.writeFileSync(file, content);
  console.log('_layout.tsx updated with browser-settings!');
} else {
  console.log('browser-settings already in _layout.tsx');
}
