import fs from 'node:fs';
const file=process.argv[2];
if(!file||!fs.existsSync(file)){
  console.error('Usage: node scripts/verify-backend-contract.mjs /path/to/server.mjs');
  process.exit(2);
}
const s=fs.readFileSync(file,'utf8');
const fixed=[
  '/api/student/login','/api/student/change-password','/api/student/dashboard',
  '/api/student/homework/submit','/api/student/app-version','/api/student/exam-incidents','/api/student/online-class/join','/api/student/profile','/api/student/logout'
];
const missing=fixed.filter(x=>!s.includes(`"${x}"`) && !s.includes(`'${x}'`));
const requiredSnippets=[
  ['exam download','student\\/exams\\/','\\/download'],
  ['mcq submit','student\\/exams\\/','\\/submit'],
  ['mixed submit','submit-mixed'],
  ['descriptive submit','submit-descriptive']
];
const missingDynamic=requiredSnippets.filter(([, ...snippets])=>!snippets.every(x=>s.includes(x))).map(x=>x[0]);
const fields=['todaySchedule','weeklySchedule','homework','exams','notifications','attendance','termReports','resources'];
const missingFields=fields.filter(x=>!s.includes(x));
if(missing.length||missingFields.length||missingDynamic.length){
  console.error('Contract FAIL', {missing,missingDynamic,missingFields});
  process.exit(1);
}
console.log('Contract PASS: dashboard, homework, profile, online class and offline-exam delivery endpoints found.');
