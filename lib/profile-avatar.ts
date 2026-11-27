export const PROFILE_AVATAR_CHANGED = 'getlancer:profile-avatar-changed';

export function profileAvatarUrl(value: unknown) {
  return typeof value === 'string' && /^\/api\/v1\/profile-avatars\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)
    ? value : undefined;
}
