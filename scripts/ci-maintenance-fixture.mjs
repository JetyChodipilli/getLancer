// Disposable CI prior-delivery fixture; never runtime seeds or real provider-payment evidence.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
export async function maintenanceFixtures({query,builder,client,buyerId,sellerId,productId}){
 assert.equal(process.env.CI,'true');assert.match(process.env.COMPOSE_PROJECT_NAME||'',/^getlancer-ci-[0-9]+$/);assert.equal(query('SELECT current_database()'),'getLancer');
 for(const value of [buyerId,sellerId,productId])assert.match(value,/^[a-f0-9-]{36}$/);
 const fixtures={};
 for(const device of ['smoke','desktop','phone','tablet']){
  const inquiry=randomUUID();
  query(`INSERT INTO getlancer.inquiries(id,reference_product_id,developer_user_id,client_email,client_name,request_type,description,budget_band,timeline_band,idempotency_key,request_hash,current_status,email_confirmed_at) VALUES('${inquiry}','${productId}','${sellerId}','ci-client@example.test','CI Client','CUSTOMIZE','Disposable prior delivery fixture','NEED_ESTIMATE','FLEXIBLE','${randomUUID()}','ci-only','DISCUSSION',now())`);
  const engagement=await builder('/api/v1/engagements',{inquiryId:inquiry}),root='/api/v1/engagements/'+engagement.id;
  const proposal=await builder(root+'/proposals',{scope:'Deliver a bounded approval workflow for the synthetic CI client.',terms:'Synthetic acceptance terms. No live merchant charge is performed in CI.',milestones:[{title:'CI approval workflow',description:'Delivered and tested the synthetic workflow.',amountMinor:10000,dueDate:'2030-12-31'}]});
  await builder(root+'/proposals/'+proposal.id+'/send',{consent:true});await client(root+'/proposals/'+proposal.id+'/accept',{consent:true});
  const milestone=(await builder(root)).milestones[0];await builder(root+'/milestones/'+milestone.id+'/start',{});await builder(root+'/milestones/'+milestone.id+'/submit',{deliveryNote:'Synthetic connected delivery evidence; provider settlement is not asserted.'});await client(root+'/milestones/'+milestone.id+'/accept',{});
  // Prior live-paid eligibility is a labelled DB fixture only. Provider HTTP billing is tested separately.
  query(`INSERT INTO getlancer.payment_attempts(id,milestone_id,payer_user_id,idempotency_key,amount_minor,account_id,mode,status,order_id,payment_id,transfer_status) VALUES('${randomUUID()}','${milestone.id}','${buyerId}','${randomUUID()}',10000,'acc_ci_fixture123','live','CAPTURED','order_${randomUUID().replaceAll('-','')}','pay_${randomUUID().replaceAll('-','')}','PROCESSED')`);
  await builder(root+'/completion',{});await client(root+'/completion',{});assert.equal((await client(root)).status,'COMPLETED');fixtures[device]=engagement;
  if(device==='smoke'){
   const offer=await builder('/api/v1/maintenance',{engagementId:engagement.id,title:'CI retained care',scope:'Bounded support for the delivered synthetic approval workflow.',terms:'One request per paid period. Synthetic retention fixture; test payments do not grant real support.',amountMinor:10000,requestsPerCycle:1,responseHours:24,totalCycles:12});
   await builder('/api/v1/maintenance/'+offer.id+'/send',{consent:true});const detail=await client('/api/v1/maintenance/'+offer.id);await client('/api/v1/maintenance/'+offer.id+'/accept',{consent:true,digest:detail.digest});
   const subscription=randomUUID(),period=randomUUID(),request=randomUUID();
   query(`INSERT INTO getlancer.maintenance_subscriptions(id,engagement_id,offer_id,payer_id,request_key,amount_minor,currency,requests_per_cycle,response_hours,total_cycles,digest,account_id,mode,status,creation_step,plan_state,subscription_state,provider_plan_id,provider_subscription_id,reconciled_at) VALUES('${subscription}','${engagement.id}','${offer.id}','${buyerId}','${randomUUID()}',10000,'INR',1,24,12,'${detail.digest}','acc_ci_fixture123','test','ACTIVE','COMPLETE','CONFIRMED','CONFIRMED','plan_ci_fixture123','sub_ci_fixture123',now())`);
   query(`INSERT INTO getlancer.maintenance_periods(id,subscription_id,provider_invoice_id,payment_id,order_id,amount_minor,currency,period_start,period_end,transfer_key,transfer_state,transfer_id,transfer_status,reconciled_at) VALUES('${period}','${subscription}','inv_ci_fixture123','pay_ci_care123','order_ci_care123',10000,'INR',now()-interval '1 day',now()+interval '20 days','${randomUUID()}','CONFIRMED','trf_ci_fixture123','CREATED',now())`);
   query(`INSERT INTO getlancer.maintenance_requests(id,subscription_id,period_id,actor_id,request_key,title,description,response_due_at,status) VALUES('${request}','${subscription}','${period}','${buyerId}','${randomUUID()}','CI consumed slot','Synthetic retained request tests consumed quota across restore.',now()+interval '24 hours','CANCELLED')`);
   query(`INSERT INTO getlancer.maintenance_ledger(id,period_id,entry_key,kind,amount_minor) VALUES('${randomUUID()}','${period}','ci-retained-capture','CAPTURE',10000)`);
   const care=await client('/api/v1/maintenance/'+offer.id);assert.equal(care.entitlement.available,false);assert.equal(care.entitlement.usedRequests,1);fixtures.restore={subscription,period,request};
  }
 }
 console.log('Connected V4 prerequisites: real delivery/maintenance APIs plus labelled disposable financial fixtures; no merchant charges or production entitlement asserted.');return fixtures;
}
