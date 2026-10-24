/** Preserve the viewport footprint while the async catalogue or detail page resolves.
 * Otherwise the shared footer renders in the first viewport and jumps when
 * streamed content replaces this loading boundary (measured as CLS).
 */
export default function Loading(){return <main id="main" className="kit-page" style={{minHeight:'100svh'}} aria-busy="true"><p role="status">Loading components…</p></main>;}
