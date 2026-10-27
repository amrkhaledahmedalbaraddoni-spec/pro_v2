const fs = require('fs');
const file = 'D:/RED_Golden/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/ui/ModernRedDashboard.kt';
let content = fs.readFileSync(file, 'utf8');

content = content.replace(
  /\(fadeIn\(animationSpec = tween\(220\)\) \+\s*slideInVertically\(animationSpec = tween\(240, easing = FastOutSlowInEasing\)\) \{ it \/ 24 \}\s*\)\.togetherWith\(fadeOut\(animationSpec = tween\(120\)\)\)/g,
  `(fadeIn(animationSpec = tween(300)) +\n                                slideInHorizontally(animationSpec = SpringSnappy) { fullWidth -> if (targetState.ordinal > initialState.ordinal) fullWidth / 2 else -fullWidth / 2 }\n                            ).togetherWith(\n                                fadeOut(animationSpec = tween(200)) +\n                                slideOutHorizontally(animationSpec = SpringSnappy) { fullWidth -> if (targetState.ordinal > initialState.ordinal) -fullWidth / 2 else fullWidth / 2 }\n                            )`
);

fs.writeFileSync(file, content);
console.log('ModernRedDashboard.kt animations updated!');
