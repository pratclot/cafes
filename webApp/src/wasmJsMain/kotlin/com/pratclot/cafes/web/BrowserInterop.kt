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

internal fun browserRequestPhotos(onComplete: (String) -> Unit): Unit = js(
  """
  {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.multiple = true;
    input.setAttribute('capture', 'environment');
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
