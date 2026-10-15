const status = document.querySelector('#status');
async function request(path, body, method = 'POST') {
  const response = await fetch(path, body ? { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {});
  const value = await response.json(); if (!response.ok) throw Error(value.error || 'Request failed'); return value;
}
async function render() {
  const value = await request('/api/tasks'); document.querySelector('#remaining').textContent = `${value.remainingMinutes} minutes remaining`;
  const list = document.querySelector('#tasks'); list.replaceChildren();
  for (const task of value.tasks) {
    const item = document.createElement('li'), label = document.createElement('label'), check = document.createElement('input');
    check.type = 'checkbox'; check.checked = task.done; label.append(check, document.createTextNode(`${task.title} (${task.minutes} min)`)); item.append(label); list.append(item);
    check.addEventListener('change', async () => { try { await request(`/api/tasks/${task.id}`, { done: check.checked }, 'PATCH'); await render(); status.textContent = 'Saved locally'; } catch (error) { check.checked = !check.checked; status.textContent = error.message; } });
  }
}
document.querySelector('form').addEventListener('submit', async event => {
  event.preventDefault(); const form = event.currentTarget, button = form.querySelector('button'); button.disabled = true;
  try { await request('/api/tasks', { title: form.elements.title.value, minutes: Number(form.elements.minutes.value) }); form.reset(); await render(); status.textContent = 'Task saved locally'; form.elements.title.focus(); }
  catch (error) { status.textContent = error.message; } finally { button.disabled = false; }
});
render().catch(error => { status.textContent = error.message; });
