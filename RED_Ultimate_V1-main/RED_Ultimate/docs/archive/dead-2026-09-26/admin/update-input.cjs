const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/components/ui/input.tsx';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',/,
  "'flex h-10 w-full rounded-md border border-input/50 bg-background/50 px-3 py-2 text-sm ring-offset-background transition-all duration-300 file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:border-primary/50 focus-visible:ring-2 focus-visible:ring-primary/30 focus-visible:ring-offset-2 focus-visible:shadow-[0_0_10px_rgba(0,229,155,0.2)] disabled:cursor-not-allowed disabled:opacity-50',"
);

fs.writeFileSync(file, content);
console.log('input.tsx updated!');
