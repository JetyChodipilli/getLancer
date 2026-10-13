<script>
 let filter=$state('all');
 let completed=$state([]);
 const items=[{id:1,title:'Sensor panel',category:'UI'},{id:2,title:'Queue worker',category:'Backend'},{id:3,title:'Release notes',category:'UI'}];
 let visible=$derived(items.filter(item=>filter==='all'||item.category===filter));
 function finish(id){completed=completed.includes(id)?completed.filter(value=>value!==id):[...completed,id];}
</script>
<main><p class="kicker">Svelte · reactive list</p><h1>A small queue. Clear progress.</h1><p>An original filtered build queue. Completion is local synthetic state.</p><section class="panel" aria-labelledby="queue-heading"><h2 id="queue-heading">Build queue</h2><div class="row"><label for="queue-filter">Filter queue</label><select id="queue-filter" bind:value={filter}><option value="all">All work</option><option value="UI">UI</option><option value="Backend">Backend</option></select></div><ul aria-label="Build queue items">{#each visible as item (item.id)}<li><strong>{item.title}</strong><p class="meta">{item.category} · {completed.includes(item.id)?'Complete':'Ready'}</p><button aria-pressed={completed.includes(item.id)} onclick={()=>finish(item.id)}>{completed.includes(item.id)?'Reopen':'Complete'} {item.title}</button></li>{/each}</ul><output aria-live="polite">{completed.length} of {items.length} complete · {visible.length} visible</output><div class="row"><button onclick={()=>{filter='all';completed=[]}}>Reset queue</button></div></section></main>
