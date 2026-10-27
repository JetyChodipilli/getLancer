import {expect,test} from '@playwright/test';
import {build} from 'esbuild';

async function pickerFixture(page:import('@playwright/test').Page,initial='Inventory'){
  const fixture=await build({stdin:{contents:`
    import {useState} from 'react';
    import {createRoot} from 'react-dom/client';
    import {Picker} from './app/components/ui';
    function Fixture(){
      const [value,setValue]=useState(${JSON.stringify(initial)});
      return <section className="panel form" aria-label="Taxonomy selection">
        <h1>Choose a project category</h1>
        <label>Business category</label>
        <Picker label="Business category" value={value} onChange={setValue} options={['Inventory','ALL','value:Inventory']}/>
        <p role="status">Selected category: {value||'(none)'}</p>
      </section>;
    }
    createRoot(document.getElementById('round2-ui-root')).render(<Fixture/>);
  `,loader:'tsx',resolveDir:process.cwd()},bundle:true,write:false,platform:'browser',format:'iife',jsx:'automatic',alias:{'@':process.cwd()},define:{'process.env.NODE_ENV':'"test"'}});
  // Borrow the application's actual compiled styles while keeping the component
  // fixture outside the app's hydration tree. Its route is a test document, so
  // injecting the test bundle does not bypass or weaken the application's CSP.
  await page.goto('/report');
  const styles=await page.locator('link[rel="stylesheet"],style').evaluateAll(nodes=>nodes.map(node=>node.outerHTML).join('\n'));
  expect(styles).not.toBe('');
  await page.route('**/__round2/ui',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1">'+styles+'</head><body><main id="main" class="wrap"><div id="round2-ui-root"></div></main></body></html>'}));
  await page.goto('/__round2/ui');
  await page.addScriptTag({content:fixture.outputFiles[0].text});
  await expect(page.getByRole('region',{name:'Taxonomy selection',exact:true})).toBeVisible();
  await page.evaluate(()=>document.fonts.ready);
}

test('keyboard selection preserves a category named ALL and keeps clear distinct',async({page})=>{
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await pickerFixture(page);
  const trigger=page.getByRole('combobox',{name:'Business category',exact:true});
  await expect(trigger).toHaveText('Inventory');
  await trigger.focus();
  await page.keyboard.press('ArrowDown');
  await expect(page.getByRole('listbox')).toBeVisible();
  await expect(page.getByRole('option',{name:'Inventory',exact:true})).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('status')).toHaveText('Selected category: ALL');
  await expect(trigger).toHaveText('ALL');
  await trigger.click();
  await page.getByRole('option',{name:'Business category',exact:true}).click();
  await expect(page.getByRole('status')).toHaveText('Selected category: (none)');
  await trigger.click();
  await page.getByRole('option',{name:'value:Inventory',exact:true}).click();
  await expect(page.getByRole('status')).toHaveText('Selected category: value:Inventory');
  await expect(trigger).toBeFocused();
  expect(errors).toEqual([]);
});

test('an existing ALL category stays selected and the picker fits at 320px and enlarged text',async({page})=>{
  await page.setViewportSize({width:320,height:812});
  await pickerFixture(page,'ALL');
  await expect(page.locator('meta[name="viewport"]')).toHaveAttribute('content',/width=device-width/);
  const trigger=page.getByRole('combobox',{name:'Business category',exact:true});
  await expect(trigger).toHaveText('ALL');
  await expect(trigger).toHaveCSS('font-size','16px');
  await page.addStyleTag({content:'#round2-ui-root :is(label,p,[data-slot=select-trigger]){font-size:200% !important}'});
  await trigger.click();
  await expect(page.getByRole('option',{name:'ALL',exact:true})).toHaveAttribute('aria-selected','true');
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(321);
  expect(await page.getByRole('region',{name:'Taxonomy selection',exact:true}).evaluate(node=>node.scrollWidth<=node.clientWidth+1)).toBe(true);
});
