'use client';

import {useEffect, useRef, useState} from 'react';

const frameTime = 0.1;

function showStill(video: HTMLVideoElement) {
  video.pause();
  if (Number.isFinite(video.duration)) {
    video.currentTime = Math.min(frameTime, video.duration / 2);
  }
}

export default function HoverPreviewVideo({src, poster, title}: {src: string; poster?: string; title: string}) {
  const preview = useRef<HTMLVideoElement>(null);
  const hovering = useRef(false);
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    const video = preview.current;
    if (!video) return;
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const stopForPreference = () => {
      if (motion.matches || document.hidden) {
        hovering.current = false;
        setPlaying(false);
        showStill(video);
      }
    };
    motion.addEventListener('change', stopForPreference);
    document.addEventListener('visibilitychange', stopForPreference);
    return () => {
      motion.removeEventListener('change', stopForPreference);
      document.removeEventListener('visibilitychange', stopForPreference);
      hovering.current = false;
      video.pause();
    };
  }, [src]);

  return <span className="listing-hover-preview"><video
    ref={preview}
    className="listing-preview-video listing-hover-video"
    src={src+'#t=0.1'}
    poster={poster}
    aria-label={title+' video preview'}
    muted
    playsInline
    loop
    preload="metadata"
    onPlaying={() => setPlaying(hovering.current)}
    onPause={() => setPlaying(false)}
    onLoadedMetadata={event => {
      if (!hovering.current) showStill(event.currentTarget);
    }}
    onPointerEnter={event => {
      if (event.pointerType !== 'mouse' || !matchMedia('(any-hover: hover)').matches
        || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      const video = event.currentTarget;
      hovering.current = true;
      video.muted = true;
      void video.play().then(() => {
        if (!hovering.current) showStill(video);
      }).catch(() => {
        if (hovering.current) showStill(video);
      });
    }}
    onPointerLeave={event => {
      hovering.current = false;
      setPlaying(false);
      showStill(event.currentTarget);
    }}
  />{poster && !playing && <img className="listing-hover-poster" src={poster} alt=""
    onError={event => { event.currentTarget.style.visibility = 'hidden'; }} />}</span>;
}
