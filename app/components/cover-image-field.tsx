'use client';

import { useEffect, useId, useState } from 'react';

export default function CoverImageField({ imageUrl, videoUrl, disabled }: { imageUrl?: string; videoUrl?: string; disabled?: boolean }) {
  const id = useId();
  const [selection, setSelection] = useState<{ url: string; video: boolean }>();
  const [error, setError] = useState('');

  useEffect(() => {
    if (!selection) return;
    return () => URL.revokeObjectURL(selection.url);
  }, [selection]);

  const media = selection?.url || videoUrl || imageUrl;
  const video = selection?.video ?? !!videoUrl;
  return (
    <section className="cover-image-field">
      <div className="showcase-field">
        <label htmlFor={id}>Upload preview media</label>
        <p className="muted" id={id+'-hint'}>Choose one screenshot (PNG or JPEG) or a short MP4, up to 5 MB.</p>
        <input
          id={id}
          name="cover"
          type="file"
          accept="image/png,image/jpeg,video/mp4,.mp4"
          disabled={disabled}
          aria-describedby={id+'-hint'+(error ? ' '+id+'-error' : '')}
          aria-invalid={!!error || undefined}
          onChange={event => {
            const file = event.currentTarget.files?.[0];
            if (file && (!['image/png','image/jpeg','video/mp4'].includes(file.type) || !file.size || file.size > 5242880)) {
              event.currentTarget.value = '';
              setSelection(undefined);
              setError('Choose a PNG, JPEG or MP4 up to 5 MB.');
              return;
            }
            setError('');
            setSelection(file ? { url: URL.createObjectURL(file), video: file.type === 'video/mp4' } : undefined);
          }}
        />
        {error && <p id={id+'-error'} className="error" role="alert">{error}</p>}
      </div>
      {media && <figure className="cover-image-preview">
        {video ? <video key={media} src={media} aria-label="Listing video preview" controls playsInline preload="metadata" />
          : <img src={media} alt="Listing cover preview" />}
        <figcaption>Card preview</figcaption>
      </figure>}
    </section>
  );
}
