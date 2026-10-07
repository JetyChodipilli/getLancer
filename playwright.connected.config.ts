import assert from 'node:assert/strict';
import {defineConfig} from '@playwright/test';
import preview from './playwright.config';

assert.equal(process.env.CI,'true','Connected mutations run only in disposable CI.');
assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);

export default defineConfig({
 ...preview,testDir:'./tests/connected',timeout:180000,
 outputDir:'test-results/connected',
 use:{...preview.use,baseURL:'http://localhost:3000',actionTimeout:15000},
 webServer:{command:'npm run start -- --port 3000',url:'http://localhost:3000',reuseExistingServer:false,timeout:120000,env:{APP_ENV:'local',DEMO_PUBLIC_URL_TEMPLATE:'http://{id}.demo.localhost:8090',BACKEND_URL:'http://localhost:8080',DEMO_MODE:'true'}},
});
