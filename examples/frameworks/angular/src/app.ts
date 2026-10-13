import {Component,signal,computed,provideZonelessChangeDetection} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
@Component({selector:'native-preview',standalone:true,template:`
<main><p class="kicker">Angular · signal checklist</p><h1>Ship with a clear checklist.</h1><p>An original release checklist. These checks are local examples, not acceptance evidence.</p><section class="panel" aria-labelledby="release-heading"><h2 id="release-heading">Release checklist</h2><p><strong class="count">{{count()}} / 3</strong> checks complete</p><ul>@for (item of items; track item.id){<li class="checkbox"><input [id]="'check-'+item.id" type="checkbox" [checked]="checked().includes(item.id)" (change)="toggle(item.id)"><label [for]="'check-'+item.id">{{item.title}}</label></li>}</ul><output aria-live="polite">{{count()===3?'Ready for a local review.':(3-count())+' checks remaining.'}}</output><div class="row"><button (click)="checked.set([])">Reset checklist</button></div></section></main>`})
class ReleaseChecklist {
 readonly items=[{id:1,title:'Review interface'},{id:2,title:'Check keyboard flow'},{id:3,title:'Inspect source rights'}];
 readonly checked=signal<number[]>([]);
 readonly count=computed(()=>this.checked().length);
 toggle(id:number){this.checked.update(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id]);}
}
bootstrapApplication(ReleaseChecklist,{providers:[provideZonelessChangeDetection()]}).catch(()=>{document.body.textContent='The native preview could not start. Download source to reproduce it locally.';});
