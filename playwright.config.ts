import {defineConfig} from '@playwright/test';
export default defineConfig({
 testDir:'./tests/browser',fullyParallel:false,workers:1,retries:0,timeout:45000,
 reporter:[['list']],use:{baseURL:'http://127.0.0.1:3000',trace:'retain-on-failure',screenshot:'only-on-failure'},
 projects:[
  {name:'desktop',use:{browserName:'chromium',viewport:{width:1440,height:1000}}},
  {name:'phone',use:{browserName:'chromium',viewport:{width:375,height:812},isMobile:true,hasTouch:true}},
  {name:'tablet',use:{browserName:'chromium',viewport:{width:768,height:1024},hasTouch:true}},
 ],
 webServer:{command:'npm run start -- --port 3000',url:'http://127.0.0.1:3000',reuseExistingServer:false,timeout:120000,env:{DEMO_MODE:'true',BACKEND_URL:''}},
});
