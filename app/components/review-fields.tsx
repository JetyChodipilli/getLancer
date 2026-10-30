export default function ReviewFields({prefix}: {prefix: string}) {
  return <fieldset className="paper-review-fields">
    <legend>Share your experience</legend>
    <p className="field-hint">Help others understand what it was like to work together.</p>
    <label htmlFor={prefix+'-rating'}>Rating (1–5)</label>
    <input id={prefix+'-rating'} name="rating" type="number" min={1} max={5} step={1} inputMode="numeric" required aria-describedby={prefix+'-rating-hint'}/>
    <span id={prefix+'-rating-hint'} className="field-hint">1 means poor; 5 means excellent.</span>
    <label htmlFor={prefix+'-review'}>Your experience</label>
    <textarea id={prefix+'-review'} name="reviewText" minLength={10} maxLength={2000} required aria-describedby={prefix+'-review-hint'}/>
    <span id={prefix+'-review-hint'} className="field-hint">Describe the communication, quality of work, and delivery.</span>
    <label htmlFor={prefix+'-identity'}>Name shown with your review</label>
    <select id={prefix+'-identity'} name="visibility" defaultValue="ANONYMOUS" aria-describedby={prefix+'-privacy'}>
      <option value="ANONYMOUS">Verified client — keep my name private</option>
      <option value="NAMED">Show the name I used for this inquiry</option>
    </select>
    <p id={prefix+'-privacy'} className="muted">Your email is never displayed. Your review is checked before publication; avoid including confidential project details.</p>
  </fieldset>;
}
