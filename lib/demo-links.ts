export const demoPublishingPath = '/preview/workspace?view=publishing';
export const demoSavedPath = '/preview/workspace?view=saved';

export function demoProjectPath(view: 'project' | 'inquiry' | 'report', slug: string) {
  return '/preview/workspace?' + new URLSearchParams({view, project:slug});
}
