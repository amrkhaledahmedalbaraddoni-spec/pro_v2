const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/components/ui/card.tsx';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /'rounded-xl border bg-card text-card-foreground shadow-sm',/g,
  "'rounded-xl border border-border/60 bg-card/70 backdrop-blur-md text-card-foreground shadow-lg transition-all duration-300 hover:shadow-[0_8px_30px_rgb(0,0,0,0.12)] hover:border-primary/40',"
);

fs.writeFileSync(file, content);
console.log('card.tsx updated with Cyberpunk glassmorphism aesthetics!');
