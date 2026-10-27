const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/components/ui/badge.tsx';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /default: 'border-transparent bg-primary text-primary-foreground hover:bg-primary\/80',/,
  "default: 'border-primary/30 bg-primary/10 text-primary hover:bg-primary/20 backdrop-blur-sm',"
);
content = content.replace(
  /destructive: 'border-transparent bg-destructive text-destructive-foreground hover:bg-destructive\/80',/,
  "destructive: 'border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20 backdrop-blur-sm',"
);
content = content.replace(
  /danger: 'border-transparent bg-destructive text-destructive-foreground hover:bg-destructive\/80',/,
  "danger: 'border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20 backdrop-blur-sm',"
);
content = content.replace(
  /success: 'border-transparent bg-green-500 text-white hover:bg-green-600',/,
  "success: 'border-green-500/30 bg-green-500/10 text-green-500 hover:bg-green-500/20 backdrop-blur-sm',"
);
content = content.replace(
  /green: 'border-transparent bg-green-500 text-white hover:bg-green-600',/,
  "green: 'border-green-500/30 bg-green-500/10 text-green-500 hover:bg-green-500/20 backdrop-blur-sm',"
);
content = content.replace(
  /warning: 'border-transparent bg-yellow-500 text-white hover:bg-yellow-600',/,
  "warning: 'border-yellow-500/30 bg-yellow-500/10 text-yellow-500 hover:bg-yellow-500/20 backdrop-blur-sm',"
);
content = content.replace(
  /gold: 'border-transparent bg-amber-500 text-white hover:bg-amber-600',/,
  "gold: 'border-amber-500/30 bg-amber-500/10 text-amber-500 hover:bg-amber-500/20 backdrop-blur-sm',"
);
content = content.replace(
  /info: 'border-transparent bg-blue-500 text-white hover:bg-blue-600',/,
  "info: 'border-blue-500/30 bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 backdrop-blur-sm',"
);
content = content.replace(
  /blue: 'border-transparent bg-blue-500 text-white hover:bg-blue-600',/,
  "blue: 'border-blue-500/30 bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 backdrop-blur-sm',"
);

fs.writeFileSync(file, content);
console.log('badge.tsx updated!');
