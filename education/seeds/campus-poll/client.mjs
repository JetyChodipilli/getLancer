const status = document.querySelector('#status');
// Kept for this page session only. Retrying an uncertain request reuses the ballot.
const ballotId = crypto.randomUUID();
function render(value) { const list = document.querySelector('#totals'); list.replaceChildren(); value.choices.forEach((choice, index) => { const item = document.createElement('li'); item.textContent = `${choice}: ${value.counts[index]}`; list.append(item); }); }
document.querySelector('form').addEventListener('submit', async event => {
  event.preventDefault(); const form = event.currentTarget, button = form.querySelector('button'); button.disabled = true;
  try { const response = await fetch('/api/ballots', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ballotId, choice: Number(form.elements.choice.value) }) }); const value = await response.json(); if (!response.ok) throw Error(value.error); render(value); status.textContent = value.replay ? 'Same synthetic ballot already saved' : 'Synthetic ballot saved locally'; }
  catch (error) { status.textContent = error.message; } finally { button.disabled = false; }
});
fetch('/api/poll').then(async response => { if (!response.ok) throw Error('Local totals unavailable'); render(await response.json()); }).catch(error => { status.textContent = error.message; });
