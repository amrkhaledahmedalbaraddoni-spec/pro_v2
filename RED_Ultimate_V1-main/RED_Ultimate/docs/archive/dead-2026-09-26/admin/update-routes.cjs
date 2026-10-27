const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/routeTree.gen.ts';
let content = fs.readFileSync(file, 'utf8');

if (!content.includes('BrowserSettingsRouteImport')) {
  content = content.replace(
    /import \{ Route as BackupsRouteImport \} from '\.\/routes\/backups'/g,
    "import { Route as BackupsRouteImport } from './routes/backups'\nimport { Route as BrowserSettingsRouteImport } from './routes/browser-settings'"
  );
  content = content.replace(
    /const BackupsRoute = BackupsRouteImport\.update\(\{[\s\S]*?\} as any\)/g,
    "$& \nconst BrowserSettingsRoute = BrowserSettingsRouteImport.update({\n  id: '/browser-settings',\n  path: '/browser-settings',\n  getParentRoute: () => rootRouteImport,\n} as any)"
  );
  content = content.replace(
    /'\/backups': typeof BackupsRoute/g,
    "'/backups': typeof BackupsRoute\n  '/browser-settings': typeof BrowserSettingsRoute"
  );
  content = content.replace(
    /\| '\/backups'/g,
    "| '/backups'\n    | '/browser-settings'"
  );
  content = content.replace(
    /BackupsRoute: typeof BackupsRoute/g,
    "BackupsRoute: typeof BackupsRoute\n  BrowserSettingsRoute: typeof BrowserSettingsRoute"
  );
  content = content.replace(
    /BackupsRoute: BackupsRoute,/g,
    "BackupsRoute: BackupsRoute,\n  BrowserSettingsRoute: BrowserSettingsRoute,"
  );
  content = content.replace(
    /path: '\/backups'[\s\S]*?parentRoute: typeof rootRouteImport\n    \}/g,
    "$& \n    '/browser-settings': {\n      id: '/browser-settings'\n      path: '/browser-settings'\n      fullPath: '/browser-settings'\n      preLoaderRoute: typeof BrowserSettingsRouteImport\n      parentRoute: typeof rootRouteImport\n    }"
  );
  fs.writeFileSync(file, content);
  console.log('routeTree.gen.ts updated for browser-settings!');
}
