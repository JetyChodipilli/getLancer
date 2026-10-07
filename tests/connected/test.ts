import {readFileSync} from 'node:fs';
import {test as base} from '@playwright/test';
import {resetCiRateBudgets} from '../../scripts/ci-connected-budget.mjs';

export const test=base.extend<{isolatedRateBudgets:void}>({
 isolatedRateBudgets:[async({},use,info)=>{
  resetCiRateBudgets(JSON.parse(readFileSync('.ci-connected.json','utf8')),info.config.workers);
  await use();
 },{auto:true}],
});
export {expect,type Page} from '@playwright/test';
