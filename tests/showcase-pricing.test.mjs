import {test} from 'node:test';
import assert from 'node:assert/strict';
import {toMinorAmount,showcasePricingLabel} from '../lib/showcase-pricing.ts';
test('pricing retains exact currency minor units',()=>{
 assert.equal(toMinorAmount('12.34','USD'),1234);assert.equal(toMinorAmount('100','JPY'),100);assert.equal(toMinorAmount('1.234','KWD'),1234);
 for(const value of ['-1','1e4','12.345','NaN','1000000001'])assert.throws(()=>toMinorAmount(value,'USD'));
 assert.equal(toMinorAmount('','USD'),null);
 assert.match(showcasePricingLabel({pricingMode:'RANGE',priceMinMinor:10000,priceMaxMinor:15000,currency:'USD'}),/100.*150/);
 assert.equal(showcasePricingLabel({pricingMode:'NONE'}),null);assert.equal(showcasePricingLabel({pricingMode:'CUSTOM_QUOTE'}),'Custom quote');
});
