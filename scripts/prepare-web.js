// Builds www/ from web/Work_Tracker.html:
//  1. loads native-bridge.js BEFORE the page script,
//  2. downloads the two CDN libraries so the app also works offline (falls back to the CDN link if a download fails).
const fs = require('fs');
const path = require('path');

(async () => {
  fs.mkdirSync('www', { recursive: true });
  let html = fs.readFileSync(path.join('web', 'Work_Tracker.html'), 'utf8');

  if (!html.includes('</head>')) throw new Error('No </head> found in Work_Tracker.html');
  html = html.replace('</head>', '<script src="native-bridge.js"></script>\n</head>');
  fs.copyFileSync(path.join('web', 'native-bridge.js'), path.join('www', 'native-bridge.js'));

  const libs = [
    ['https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js', 'xlsx.full.min.js'],
    ['https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js', 'jspdf.umd.min.js']
  ];
  for (const [url, file] of libs) {
    try {
      const r = await fetch(url);
      if (!r.ok) throw new Error('HTTP ' + r.status);
      fs.writeFileSync(path.join('www', file), Buffer.from(await r.arrayBuffer()));
      html = html.split(url).join(file);
      console.log('Bundled', file);
    } catch (e) {
      console.log('Could not bundle ' + file + ' (' + e.message + '), keeping the CDN link');
    }
  }
  fs.writeFileSync(path.join('www', 'index.html'), html);
  console.log('www/index.html ready');
})();
