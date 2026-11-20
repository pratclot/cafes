const PHOTO_DB_NAME = 'cafes-photos-db';
const PHOTO_STORE_NAME = 'photos';
const MAX_IMAGE_DIMENSION = 1440;
const OVERPASS_TIMEOUT_MS = 12000;

let cameraStream = null;
let cameraBounds = null;

function openPhotoDb() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = window.indexedDB.open(PHOTO_DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(PHOTO_STORE_NAME)) {
        db.createObjectStore(PHOTO_STORE_NAME);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error('IndexedDB open failed'));
  });
}

async function loadPhotoBlob(key) {
  if (!window.indexedDB) return '';
  try {
    const db = await openPhotoDb();
    return await new Promise((resolve) => {
      const tx = db.transaction(PHOTO_STORE_NAME, 'readonly');
      const req = tx.objectStore(PHOTO_STORE_NAME).get(key);
      req.onsuccess = () => resolve(typeof req.result === 'string' ? req.result : '');
      req.onerror = () => resolve('');
    });
  } catch (_) {
    return '';
  }
}

export function browserSavePhotoBlob(key, dataUrl, onDone) {
  openPhotoDb()
    .then((db) => new Promise((resolve, reject) => {
      const tx = db.transaction(PHOTO_STORE_NAME, 'readwrite');
      tx.objectStore(PHOTO_STORE_NAME).put(dataUrl, key);
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => reject(tx.error);
    }))
    .then(() => onDone(true))
    .catch(() => onDone(false));
}

export function browserLoadPhotoBlob(key, onDone) {
  loadPhotoBlob(key)
    .then(onDone)
    .catch(() => onDone(''));
}

export function browserClassifyPhotos(photos, onComplete) {
  Promise.resolve()
    .then(async () => {
      const rawList = photos.split('\n').filter(Boolean).map(decodeURIComponent);
      const resolved = await Promise.all(
        rawList.map(async (item) => {
          if (item.startsWith('idb://')) {
            const loaded = await loadPhotoBlob(item);
            return loaded || item;
          }
          return item;
        }),
      );
      return window.cafeClassifyPhotos(resolved);
    })
    .then(onComplete)
    .catch((error) => {
      const message = error && error.message ? error.message : String(error);
      onComplete('!ERROR|' + encodeURIComponent(message));
    });
}

export function browserPickImageFiles(captureCamera, multiple, onComplete) {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.multiple = multiple;
  if (captureCamera) input.setAttribute('capture', 'environment');

  input.onchange = async () => {
    const files = Array.from(input.files || []);
    if (files.length === 0) {
      onComplete('');
      return;
    }

    const readImage = (file) => new Promise((resolve) => {
      const image = new Image();
      const url = URL.createObjectURL(file);
      image.onload = () => {
        const scale = Math.min(1, MAX_IMAGE_DIMENSION / Math.max(image.width, image.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(image.width * scale));
        canvas.height = Math.max(1, Math.round(image.height * scale));
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          URL.revokeObjectURL(url);
          resolve('');
          return;
        }
        ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        URL.revokeObjectURL(url);
        resolve(encodeURIComponent(canvas.toDataURL('image/jpeg', 0.82)));
      };
      image.onerror = () => {
        URL.revokeObjectURL(url);
        resolve('');
      };
      image.src = url;
    });

    const images = (await Promise.all(files.map(readImage))).filter(Boolean);
    onComplete(images.join('\n'));
  };

  input.click();
}

function syncCameraVideo() {
  const video = document.getElementById('CameraPreview');
  if (!video) return;

  const b = cameraBounds;
  if (!b || !b.visible || b.width < 16 || b.height < 16 || !cameraStream) {
    video.style.display = 'none';
    return;
  }

  video.style.left = Math.round(b.left) + 'px';
  video.style.top = Math.round(b.top) + 'px';
  video.style.width = Math.round(b.width) + 'px';
  video.style.height = Math.round(b.height) + 'px';
  video.style.display = 'block';
  if (video.paused && video.srcObject) {
    video.play().catch(() => {});
  }
}

export function browserStartCamera(facingMode, onState) {
  const video = document.getElementById('CameraPreview');
  if (!video) {
    onState(false, 'Camera preview element is unavailable.');
    return;
  }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    onState(false, 'Live camera is not supported in this browser. Use Take photo or From disk below.');
    return;
  }

  if (cameraStream) {
    try {
      cameraStream.getTracks().forEach((t) => t.stop());
    } catch (_) {}
    cameraStream = null;
  }

  const primaryConstraints = {
    video: { facingMode: { ideal: facingMode }, width: { ideal: 1920 }, height: { ideal: 1440 } },
    audio: false,
  };

  navigator.mediaDevices.getUserMedia(primaryConstraints)
    .catch(() => navigator.mediaDevices.getUserMedia({ video: true, audio: false }))
    .then((stream) => {
      cameraStream = stream;
      video.srcObject = stream;
      video.setAttribute('playsinline', 'true');
      video.muted = true;
      syncCameraVideo();
      return video.play().catch(() => {});
    })
    .then(() => {
      syncCameraVideo();
      onState(true, '');
    })
    .catch((err) => {
      syncCameraVideo();
      onState(false, (err && err.message) ? err.message : 'Camera permission was denied or unavailable.');
    });
}

export function browserStopCamera() {
  const video = document.getElementById('CameraPreview');
  if (video) {
    video.style.display = 'none';
    video.srcObject = null;
  }
  if (cameraStream) {
    try {
      cameraStream.getTracks().forEach((t) => t.stop());
    } catch (_) {}
    cameraStream = null;
  }
}

export function browserUpdateCameraBounds(left, top, width, height, visible) {
  cameraBounds = { left, top, width, height, visible };
  syncCameraVideo();
}

export function browserCaptureVideoFrame() {
  const video = document.getElementById('CameraPreview');
  if (!video || !cameraStream || !video.videoWidth || !video.videoHeight) return '';

  const scale = Math.min(1, MAX_IMAGE_DIMENSION / Math.max(video.videoWidth, video.videoHeight));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
  canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';

  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
  return encodeURIComponent(canvas.toDataURL('image/jpeg', 0.85));
}

export function browserRequestLocation(onResult) {
  if (!navigator.geolocation) {
    onResult(NaN, NaN, 0, 'This browser does not provide location access.');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (result) => onResult(result.coords.latitude, result.coords.longitude, result.coords.accuracy, ''),
    (error) => onResult(NaN, NaN, 0, error.message || 'Location access failed.'),
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
  );
}

export async function browserFindNearbyCafes(latitude, longitude, onResult) {
  const query =
    '[out:json][timeout:12];(nwr(around:350,' + latitude + ',' + longitude + ')["amenity"="cafe"];' +
    'nwr(around:350,' + latitude + ',' + longitude + ')["shop"="coffee"];);out center tags;';

  const distance = (lat, lon) => {
    const rad = (value) => value * Math.PI / 180;
    const dLat = rad(lat - latitude);
    const dLon = rad(lon - longitude);
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos(rad(latitude)) * Math.cos(rad(lat)) * Math.sin(dLon / 2) ** 2;
    return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
  };

  const inEurope = latitude >= 34.0 && latitude <= 72.0 && longitude >= -25.0 && longitude <= 45.0;
  const endpoints = inEurope
    ? ['https://ovp.dresel.systems/api/interpreter', 'https://overpass-api.de/api/interpreter']
    : ['https://overpass-api.de/api/interpreter'];

  let lastError = null;
  for (const endpoint of endpoints) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), OVERPASS_TIMEOUT_MS);
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body: 'data=' + encodeURIComponent(query),
        signal: controller.signal,
      });
      if (!response.ok) throw new Error('Overpass HTTP ' + response.status);
      const data = await response.json();
      const values = (data.elements || [])
        .map((item) => {
          const tags = item.tags || {};
          const point = item.center || item;
          if (!Number.isFinite(point.lat) || !Number.isFinite(point.lon)) return null;
          const street = [tags['addr:street'], tags['addr:housenumber']].filter(Boolean).join(' ');
          const address = [street, tags['addr:city']].filter(Boolean).join(', ');
          const row = [
            item.type + ':' + item.id,
            tags.name || tags.brand || 'Cafe',
            address,
            String(point.lat),
            String(point.lon),
          ]
            .map((field) => encodeURIComponent(field))
            .join('|');
          return { row, distance: distance(point.lat, point.lon) };
        })
        .filter(Boolean)
        .sort((a, b) => a.distance - b.distance);
      onResult(values.map((value) => value.row).join('\n'));
      return;
    } catch (err) {
      lastError = err;
    } finally {
      clearTimeout(timer);
    }
  }

  const msg = (lastError && lastError.message) ? lastError.message : 'Overpass lookup failed';
  onResult('!ERROR|' + encodeURIComponent(msg));
}
