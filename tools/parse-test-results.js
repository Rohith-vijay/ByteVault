const fs = require('fs');
const path = require('path');

const backendDir = 'D:\\ProductManagementSystem\\backend';

function parseSurefire() {
  const modules = fs.readdirSync(backendDir, { withFileTypes: true })
    .filter(d => d.isDirectory() && fs.existsSync(path.join(backendDir, d.name, 'pom.xml')))
    .map(d => d.name);

  const results = [];

  for (const mod of modules) {
    const surefireDir = path.join(backendDir, mod, 'target', 'surefire-reports');
    let totalTests = 0;
    let totalFailures = 0;
    let totalErrors = 0;
    let totalSkipped = 0;
    let unitCount = 0;
    let integrationCount = 0;
    let contractCount = 0;
    let securityCount = 0;
    const testDetails = [];

    if (fs.existsSync(surefireDir)) {
      const files = fs.readdirSync(surefireDir).filter(f => f.startsWith('TEST-') && f.endsWith('.xml'));

      for (const file of files) {
        const content = fs.readFileSync(path.join(surefireDir, file), 'utf-8');
        const matchTests = content.match(/tests="(\d+)"/);
        const matchFailures = content.match(/failures="(\d+)"/);
        const matchErrors = content.match(/errors="(\d+)"/);
        const matchSkipped = content.match(/skipped="(\d+)"/);
        const matchName = content.match(/name="([^"]+)"/);

        const count = matchTests ? parseInt(matchTests[1], 10) : 0;
        const failures = matchFailures ? parseInt(matchFailures[1], 10) : 0;
        const errors = matchErrors ? parseInt(matchErrors[1], 10) : 0;
        const skipped = matchSkipped ? parseInt(matchSkipped[1], 10) : 0;
        const className = matchName ? matchName[1] : file;

        totalTests += count;
        totalFailures += failures;
        totalErrors += errors;
        totalSkipped += skipped;

        let category = 'Unit';
        const lowerName = className.toLowerCase();
        if (lowerName.includes('security') || lowerName.includes('idor') || lowerName.includes('auth')) {
          category = 'Security';
          securityCount += count;
        } else if (lowerName.includes('contract')) {
          category = 'Contract';
          contractCount += count;
        } else if (lowerName.includes('integration') || lowerName.includes('e2e') || lowerName.includes('idempotency') || lowerName.includes('reliability') || lowerName.includes('outbox')) {
          category = 'Integration';
          integrationCount += count;
        } else {
          category = 'Unit';
          unitCount += count;
        }

        testDetails.push({ className, count, failures, errors, skipped, category });
      }
    }

    results.push({
      module: mod,
      totalTests,
      unitCount,
      integrationCount,
      contractCount,
      securityCount,
      totalFailures,
      totalErrors,
      totalSkipped,
      testDetails
    });
  }

  return results;
}

if (require.main === module) {
  const data = parseSurefire();
  console.log('| Module | Total | Unit | Integration | Contract | Security | Failures | Errors | Skipped | Status |');
  console.log('|---|---|---|---|---|---|---|---|---|---|');
  let grandTotal = 0;
  let grandFailures = 0;
  let grandErrors = 0;
  let grandSkipped = 0;
  for (const row of data) {
    if (row.totalTests === 0) continue;
    grandTotal += row.totalTests;
    grandFailures += row.totalFailures;
    grandErrors += row.totalErrors;
    grandSkipped += row.totalSkipped;
    const status = (row.totalFailures + row.totalErrors === 0) ? 'PASS' : 'FAIL';
    console.log(`| ${row.module} | ${row.totalTests} | ${row.unitCount} | ${row.integrationCount} | ${row.contractCount} | ${row.securityCount} | ${row.totalFailures} | ${row.totalErrors} | ${row.totalSkipped} | ${status} |`);
  }
  console.log(`| **TOTAL** | **${grandTotal}** | | | | | **${grandFailures}** | **${grandErrors}** | **${grandSkipped}** | ${grandFailures + grandErrors === 0 ? 'ALL PASS' : 'FAILURES DETECTED'} |`);
}

module.exports = { parseSurefire };
