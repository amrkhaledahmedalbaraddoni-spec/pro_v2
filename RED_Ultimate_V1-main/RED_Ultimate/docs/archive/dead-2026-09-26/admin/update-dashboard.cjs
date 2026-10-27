const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/pages/Dashboard.tsx';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">/g,
  '<div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4 animate-slide-up" style={{ animationDelay: "100ms", animationFillMode: "both" }}>'
);

content = content.replace(
  /<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">/g,
  '<div className="grid gap-6 md:grid-cols-2 lg:grid-cols-7 animate-slide-up" style={{ animationDelay: "200ms", animationFillMode: "both" }}>'
);

content = content.replace(
  /<div className="grid gap-4 md:grid-cols-2">/g,
  '<div className="grid gap-6 md:grid-cols-2 animate-slide-up" style={{ animationDelay: "300ms", animationFillMode: "both" }}>'
);

// Modernizing the "Welcome back" area
content = content.replace(
  /<div className="flex flex-col gap-2">/g,
  '<div className="flex flex-col gap-2 animate-slide-in">'
);

fs.writeFileSync(file, content);
console.log('Dashboard.tsx updated with animations and better spacing!');
