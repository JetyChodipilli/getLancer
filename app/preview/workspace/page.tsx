import WorkspacePreview from './workspace-preview';
export const metadata={title:'Interactive V1 demo',robots:{index:false,follow:false},alternates:{canonical:null}};
// Explicitly separate from account routes, even when a real backend is configured.
export default function Page(){return <WorkspacePreview/>}
