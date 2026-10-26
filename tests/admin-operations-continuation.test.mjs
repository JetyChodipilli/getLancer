import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {buildSync} from 'esbuild';
const code=buildSync({entryPoints:['app/components/delivery-operations.tsx'],bundle:true,write:false,platform:'node',format:'esm',jsx:'automatic'}).outputFiles[0].text;
const {operationPage,appendOperationRecords}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
const row=n=>({id:'record-'+n,status:'OPEN'});
const page=(items,number=0)=>({items,page:number,size:50,hasMore:true,totalItems:206,totalPages:5});

test('operations accepts only bounded verifiable pages and does not silently treat legacy truncation as complete',()=>{
 assert.equal(operationPage(page([row(0)]),0).items[0].id,'record-0');
 for(const value of [null,[],{}, {items:[row(0)]}, {...page([]),page:1}, {...page([]),size:0}, {...page([]),size:101}, {...page([]),hasMore:'true'}, {...page([]),totalItems:-1}, {...page([]),totalPages:1.5},page([null]),page([{}]),page(Array.from({length:51},(_,n)=>row(n)))])assert.throws(()=>operationPage(value,0),/could not be verified/);
});
test('all 206 retained disputes stay reachable; duplicate records are updated without duplicate controls',()=>{
 const records=Array.from({length:206},(_,n)=>row(n));let loaded=[];
 for(let number=0;number<5;number++)loaded=appendOperationRecords(loaded,operationPage(page(records.slice(number*50,(number+1)*50),number),number).items);
 assert.equal(loaded.length,206);assert.equal(loaded.at(-1).id,'record-205');
 loaded=appendOperationRecords(loaded,[{...row(205),status:'RESOLVED'}]);assert.equal(loaded.length,206);assert.equal(loaded.at(-1).status,'RESOLVED');assert.equal(records.at(-1).status,'OPEN');
});
test('all admin collection queries use bounded shared pagination, stable ordering and current admin authorization',()=>{
 const delivery=readFileSync('backend/src/main/java/com/getlancer/delivery/DeliveryRepository.java','utf8');
 const payment=readFileSync('backend/src/main/java/com/getlancer/payments/PaymentRepository.java','utf8');
 const service=readFileSync('backend/src/main/java/com/getlancer/payments/PaymentService.java','utf8');
 const deliveryService=readFileSync('backend/src/main/java/com/getlancer/delivery/DeliveryService.java','utf8');
 assert.match(delivery,/disputes\(HttpServletRequest request\)[\s\S]*?Pages\.query\(db, request,[\s\S]*?ORDER BY \(d.status='OPEN'\) DESC,d.created_at DESC,d.id/);
 assert.doesNotMatch(delivery,/d.created_at DESC LIMIT 200/);
 assert.match(payment,/accounts\(HttpServletRequest request\)[\s\S]*?Pages\.query\(db, request,[\s\S]*?ORDER BY verified_at DESC,id/);
 assert.match(payment,/attention\(HttpServletRequest request\)[\s\S]*?Pages\.query\(db, request,[\s\S]*?ORDER BY p.created_at,p.id/);
 assert.match(service,/accounts\(HttpServletRequest request\) \{ security.admin\(request\); return PageResponse.from\(repo.accounts\(request\),PaymentResponses.Account::from\)/);
 assert.match(service,/attention\(HttpServletRequest request\) \{ security.admin\(request\); return PageResponse.from\(repo.attention\(request\),PaymentResponses.Attention::from\)/);
 assert.match(deliveryService,/adminDisputes\(HttpServletRequest request\) \{\s*repo.admin\(request\);\s*return repo.disputes\(request\)/);
});
