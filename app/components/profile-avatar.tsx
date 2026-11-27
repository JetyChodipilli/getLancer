'use client';
import {useState} from 'react';
import './profile-avatar.css';

export default function ProfileAvatar({name, src, className = ''}: {name: string; src?: string; className?: string}) {
  const [failed, setFailed] = useState<string>();
  const initials = name.trim().split(/\s+/).filter(Boolean).slice(0, 2)
    .map(word => Array.from(word)[0]).join('').toUpperCase() || 'M';
  return <span className={'profile-avatar '+className} aria-hidden="true">
    {src && src !== failed
      ? <img src={src} alt="" onError={() => setFailed(src)}/>
      : initials}
  </span>;
}
