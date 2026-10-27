const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/components/ui/button.tsx';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /'inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',/,
  "'inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-semibold ring-offset-background transition-all duration-200 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 shadow-sm hover:shadow-md',"
);

content = content.replace(
  /default: 'bg-primary text-primary-foreground hover:bg-primary\/90',/g,
  "default: 'bg-primary text-primary-foreground hover:bg-primary/80 hover:shadow-[0_0_15px_rgba(0,229,155,0.4)]',"
);
content = content.replace(
  /primary: 'bg-primary text-primary-foreground hover:bg-primary\/90',/g,
  "primary: 'bg-primary text-primary-foreground hover:bg-primary/80 hover:shadow-[0_0_15px_rgba(0,229,155,0.4)]',"
);

content = content.replace(
  /destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive\/90',/g,
  "destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/80 hover:shadow-[0_0_15px_rgba(255,69,58,0.4)]',"
);
content = content.replace(
  /danger: 'bg-destructive text-destructive-foreground hover:bg-destructive\/90',/g,
  "danger: 'bg-destructive text-destructive-foreground hover:bg-destructive/80 hover:shadow-[0_0_15px_rgba(255,69,58,0.4)]',"
);

fs.writeFileSync(file, content);
console.log('button.tsx updated with active states and glowing effects!');
