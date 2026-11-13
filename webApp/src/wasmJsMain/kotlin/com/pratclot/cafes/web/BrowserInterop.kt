@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.pratclot.cafes.web

import kotlin.js.js

internal fun browserNowMillis(): Double = js("Date.now()")

internal fun encodeBrowserValue(value: String): String = js("encodeURIComponent(value)")

internal fun decodeBrowserValue(value: String): String = js("decodeURIComponent(value)")

internal fun browserStorageRead(key: String): String? = js("window.localStorage.getItem(key)")

internal fun browserStorageWrite(key: String, value: String): Boolean = js(
  """(() => { try { window.localStorage.setItem(key, value); return true; } catch (_) { return false; } })()""",
)

internal fun browserFormatDate(timestamp: Double): String = js(
  "new Date(timestamp).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })",
)

internal fun browserClassifyPhotos(photos: String, onComplete: (String) -> Unit): Unit = js(
  """
  Promise.resolve()
    .then(() => window.cafeClassifyPhotos(photos.split('\n').filter(Boolean).map(decodeURIComponent)))
    .then(onComplete)
    .catch((error) => onComplete('!ERROR|' + encodeURIComponent((error && error.message) ? error.message : String(error))))
  """,
)

internal fun browserRequestPhotos(onComplete: (String) -> Unit): Unit =
  browserPickImageFiles(captureCamera = false, multiple = true, onComplete = onComplete)

internal fun browserRequestCameraFallback(onComplete: (String) -> Unit): Unit =
  browserPickImageFiles(captureCamera = true, multiple = false, onComplete = onComplete)

private fun browserPickImageFiles(captureCamera: Boolean, multiple: Boolean, onComplete: (String) -> Unit): Unit = js(
  """
  {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.multiple = multiple;
    if (captureCamera) input.setAttribute('capture', 'environment');
    input.onchange = async () => {
      const files = Array.from(input.files || []);
      if (files.length === 0) { onComplete(''); return; }
      const readImage = (file) => new Promise((resolve) => {
        const image = new Image();
        const url = URL.createObjectURL(file);
        image.onload = () => {
          const scale = Math.min(1, 1440 / Math.max(image.width, image.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(image.width * scale));
          canvas.height = Math.max(1, Math.round(image.height * scale));
          canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
          URL.revokeObjectURL(url);
          resolve(encodeURIComponent(canvas.toDataURL('image/jpeg', 0.82)));
        };
        image.onerror = () => { URL.revokeObjectURL(url); resolve(''); };
        image.src = url;
      });
      const images = (await Promise.all(files.map(readImage))).filter(Boolean);
      onComplete(images.join('\n'));
    };
    input.click();
  }
  """,
)

internal fun browserStartCamera(facingMode: String, onState: (Boolean, String) -> Unit): Unit = js(
  """
  {
    const video = document.getElementById('CameraPreview');
    if (!video) {
      onState(false, 'Camera preview element is unavailable.');
      return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      onState(false, 'Live camera is not supported in this browser. Use Take photo or From disk below.');
      return;
    }
    const syncVideo = () => {
      const b = window.__cafeCameraBounds;
      if (!b || !b.visible || b.width < 16 || b.height < 16 || !window.__cafeCameraStream) {
        video.style.display = 'none';
      } else {
        video.style.left = Math.round(b.left) + 'px';
        video.style.top = Math.round(b.top) + 'px';
        video.style.width = Math.round(b.width) + 'px';
        video.style.height = Math.round(b.height) + 'px';
        video.style.display = 'block';
        if (video.paused && video.srcObject) {
          video.play().catch(() => {});
        }
      }
    };
    window.__syncCafeCameraVideo = syncVideo;
    if (window.__cafeCameraStream) {
      try { window.__cafeCameraStream.getTracks().forEach(t => t.stop()); } catch (_) {}
      window.__cafeCameraStream = null;
    }
    const primaryConstraints = {
      video: { facingMode: { ideal: facingMode }, width: { ideal: 1920 }, height: { ideal: 1440 } },
      audio: false
    };
    navigator.mediaDevices.getUserMedia(primaryConstraints)
      .catch(() => navigator.mediaDevices.getUserMedia({ video: true, audio: false }))
      .then((stream) => {
        window.__cafeCameraStream = stream;
        video.srcObject = stream;
        video.setAttribute('playsinline', 'true');
        video.muted = true;
        syncVideo();
        return video.play().catch(() => {});
      })
      .then(() => {
        syncVideo();
        onState(true, '');
      })
      .catch((err) => {
        syncVideo();
        onState(false, (err && err.message) ? err.message : 'Camera permission was denied or unavailable.');
      });
  }
  """,
)

internal fun browserStopCamera(): Unit = js(
  """
  {
    const video = document.getElementById('CameraPreview');
    if (video) {
      video.style.display = 'none';
      video.srcObject = null;
    }
    if (window.__cafeCameraStream) {
      try { window.__cafeCameraStream.getTracks().forEach(t => t.stop()); } catch (_) {}
      window.__cafeCameraStream = null;
    }
  }
  """,
)

internal fun browserUpdateCameraBounds(left: Double, top: Double, width: Double, height: Double, visible: Boolean): Unit = js(
  """
  {
    window.__cafeCameraBounds = { left: left, top: top, width: width, height: height, visible: visible };
    const video = document.getElementById('CameraPreview');
    if (video) {
      if (!visible || width < 16 || height < 16 || !window.__cafeCameraStream) {
        video.style.display = 'none';
      } else {
        video.style.left = Math.round(left) + 'px';
        video.style.top = Math.round(top) + 'px';
        video.style.width = Math.round(width) + 'px';
        video.style.height = Math.round(height) + 'px';
        video.style.display = 'block';
        if (video.paused && video.srcObject) {
          video.play().catch(() => {});
        }
      }
    }
  }
  """,
)

internal fun browserCaptureVideoFrame(): String = js(
  """
  (() => {
    const video = document.getElementById('CameraPreview');
    if (!video || !window.__cafeCameraStream || !video.videoWidth || !video.videoHeight) return '';
    const scale = Math.min(1, 1440 / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
    return encodeURIComponent(canvas.toDataURL('image/jpeg', 0.85));
  })()
  """,
)

internal fun browserRequestLocation(onResult: (Double, Double, Double, String) -> Unit): Unit = js(
  """
  {
    if (!navigator.geolocation) {
      onResult(NaN, NaN, 0, 'This browser does not provide location access.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (result) => onResult(result.coords.latitude, result.coords.longitude, result.coords.accuracy, ''),
      (error) => onResult(NaN, NaN, 0, error.message || 'Location access failed.'),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
  }
  """,
)

internal fun browserFindNearbyCafes(latitude: Double, longitude: Double, onResult: (String) -> Unit): Unit = js(
  """
  {
        const query = '[out:json][timeout:12];(nwr(around:350,' + latitude + ',' + longitude + ')["amenity"="cafe"];nwr(around:350,' + latitude + ',' + longitude + ')["shop"="coffee"];);out center tags;';
    const distance = (lat, lon) => {
      const rad = (value) => value * Math.PI / 180;
      const dLat = rad(lat - latitude);
      const dLon = rad(lon - longitude);
      const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(latitude)) * Math.cos(rad(lat)) * Math.sin(dLon / 2) ** 2;
      return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    };
    fetch('https://overpass-api.de/api/interpreter', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: 'data=' + encodeURIComponent(query)
    }).then((response) => {
      if (!response.ok) throw new Error('Overpass HTTP ' + response.status);
      return response.json();
    }).then((data) => {
      const values = (data.elements || []).map((item) => {
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
          String(point.lon)
        ].map((field) => encodeURIComponent(field)).join('|');
        return { row, distance: distance(point.lat, point.lon) };
      }).filter(Boolean).sort((a, b) => a.distance - b.distance);
      onResult(values.map((value) => value.row).join('\n'));
    }).catch(() => onResult('!ERROR'));
  }
  """,
)
