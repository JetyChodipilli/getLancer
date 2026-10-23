import {test,expect,type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {buildSync} from 'esbuild';
import {createRequire} from 'node:module';
import type {EducationRelease} from '../../lib/education';

const sample=JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json','utf8'))[0].release as EducationRelease;
const axe=readFileSync(createRequire(import.meta.url).resolve('axe-core/axe.min.js'),'utf8');
function release(overrides:Partial<EducationRelease>={}):EducationRelease{return {...sample,status:'APPROVED',demoHealth:'REACHABLE',demoCheckedAt:new Date(Date.now()-60000).toISOString(),demoCheckedUrl:'https://example.org/demo',demoUrl:'https://example.org/demo',snapshot:{...sample.snapshot,demoMode:'EXTERNAL',demoUrl:'https://example.org/demo'},...overrides};}
async function show(page:Page,item:EducationRelease){
 const script=buildSync({stdin:{contents:`import {createRoot} from 'react-dom/client';import Demo from './app/components/education-demo';createRoot(document.getElementById('fixture')).render(<Demo release={${JSON.stringify(item)}}/>);`,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,format:'iife',platform:'browser',jsx:'automatic',define:{'process.env.NODE_ENV':'"test"','process.env':'{}'}}).outputFiles[0].text;
 await page.route('**/education-health-fixture',route=>route.fulfill({contentType:'text/html',body:'<html lang="en"><head><title>Education health</title></head><body><main><h1>External demonstration</h1><div id="fixture"></div></main><script src="/education-health-fixture.js"></script></body></html>'}));
 await page.route('**/education-health-fixture.js',route=>route.fulfill({contentType:'application/javascript',body:script}));
 await page.goto('/education-health-fixture');
}
async function accessible(page:Page){expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);await page.evaluate(axe);const violations=await page.evaluate(async()=>(window as any).axe.run(document.querySelector('main'),{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));expect(violations.violations.filter((item:any)=>['critical','serious'].includes(item.impact)).map((item:any)=>item.id)).toEqual([]);}

test('current broken and restricted demonstrations show distinct labels without launch links',async({page})=>{
 for(const [health,label] of [['UNREACHABLE','Broken external link.'],['BLOCKED','External link blocked or restricted.']] as const){await show(page,release({demoHealth:health}));await expect(page.getByRole('status')).toContainText(label);await expect(page.getByRole('link',{name:'Open external demonstration ↗'})).toHaveCount(0);await expect(page.getByText(/Last checked:/)).toBeVisible();await accessible(page);await page.unrouteAll();}
});
test('current reachable demonstration states the limited observation and offers exact URL',async({page})=>{
 await show(page,release());await expect(page.getByRole('status')).toContainText('External link reachable.');await expect(page.getByRole('status')).toContainText('Reachability does not imply platform verification.');const link=page.getByRole('link',{name:'Open external demonstration ↗'});await expect(link).toHaveAttribute('href','https://example.org/demo');await expect(link).toHaveAttribute('rel','noopener noreferrer');await page.keyboard.press('Tab');await expect(link).toBeFocused();await accessible(page);
});
test('unchecked stale and changed URLs stay unverified without presenting an old observation',async({page})=>{
 for(const item of [release({demoCheckedAt:undefined,demoHealth:'UNKNOWN'}),release({demoCheckedAt:new Date(Date.now()-86401000).toISOString(),demoHealth:'UNREACHABLE'}),release({demoCheckedUrl:'https://example.org/old',demoHealth:'UNREACHABLE'})]){await show(page,item);await expect(page.getByRole('status')).toContainText('Unverified external link.');await expect(page.getByText(/Last checked:/)).toHaveCount(0);await expect(page.getByRole('link',{name:'Open external demonstration ↗'})).toHaveAttribute('href','https://example.org/demo');await accessible(page);await page.unrouteAll();}
});
