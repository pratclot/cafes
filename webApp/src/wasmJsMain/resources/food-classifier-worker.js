const transformersVersion = '3.3.3';
const modelId = 'Xenova/siglip-base-patch16-224';
let classifierPromise = null;

async function loadTransformersModule() {
  const urls = [
    `https://cdn.jsdelivr.net/npm/@huggingface/transformers@${transformersVersion}/dist/transformers.min.js`,
    `https://cdn.jsdelivr.net/npm/@huggingface/transformers@${transformersVersion}/+esm`,
  ];
  let lastError = null;
  for (const url of urls) {
    try {
      const mod = await import(url);
      const resolved = mod?.SiglipVisionModel ? mod : mod?.default;
      if (resolved?.SiglipVisionModel && resolved?.Tensor) {
        if (resolved.env?.backends?.onnx?.wasm) {
          resolved.env.backends.onnx.wasm.numThreads = 1;
        }
        return resolved;
      }
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError || new Error('Transformers.js runtime could not be loaded in worker.');
}

function decodeBase64Int16(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new Int16Array(bytes.buffer);
}

function getFoodClassifier(baseUrl) {
  if (!classifierPromise) {
    classifierPromise = (async () => {
      const resolvedBase = baseUrl || new URL('.', self.location.href).href;
      const [transformers, embResponse] = await Promise.all([
        loadTransformersModule(),
        fetch(new URL('food_siglip_embeddings.json', resolvedBase)),
      ]);
      if (!embResponse.ok) {
        throw new Error(`food_siglip_embeddings.json HTTP ${embResponse.status}`);
      }
      const payload = await embResponse.json();
      const embeddingsInt16 = decodeBase64Int16(payload.embeddingsBase64);
      const visionModel = await transformers.SiglipVisionModel.from_pretrained(modelId, {
        dtype: 'q8',
      });
      return { Tensor: transformers.Tensor, visionModel, payload, embeddingsInt16 };
    })().catch(err => {
      classifierPromise = null;
      throw err;
    });
  }
  return classifierPromise;
}

async function classifyPhotoViews(photoViews, baseUrl) {
  const { Tensor, visionModel, payload, embeddingsInt16 } = await getFoodClassifier(baseUrl);
  const dim = payload.dim || 768;
  const scale = payload.int16Scale;
  const bestDishScores = new Map();
  const bestIngScores = new Map();
  const viewStride = 3 * 224 * 224;

  for (const item of photoViews) {
    const nViews = item.nViews;
    const allPixels = new Float32Array(item.buffer);
    const pixelValues = new Float32Array(viewStride);
    const normViews = new Float32Array(nViews * dim);

    for (let v = 0; v < nViews; v++) {
      pixelValues.set(allPixels.subarray(v * viewStride, (v + 1) * viewStride));
      const visOut = await visionModel({
        pixel_values: new Tensor('float32', pixelValues, [1, 3, 224, 224]),
      });
      const pooler = visOut.pooler_output.data;
      let norm = 0;
      for (let i = 0; i < dim; i++) norm += pooler[i] * pooler[i];
      norm = Math.sqrt(norm) || 1;
      const off = v * dim;
      for (let i = 0; i < dim; i++) normViews[off + i] = pooler[i] / norm;
    }

    const dotPrompt = (viewIdx, promptIdx) => {
      let s = 0;
      const vOff = viewIdx * dim;
      const pOff = promptIdx * dim;
      for (let i = 0; i < dim; i++) {
        s += normViews[vOff + i] * (embeddingsInt16[pOff + i] * scale);
      }
      return s;
    };

    let pIdx = 0;
    for (const dish of payload.dishes) {
      let bestLogit = -Infinity;
      for (let c = 0; c < dish.count; c++) {
        const logit = dotPrompt(0, pIdx++) + payload.dishBias;
        if (logit > bestLogit) bestLogit = logit;
      }
      const score = 1 / (1 + Math.exp(-bestLogit));
      if (!bestDishScores.has(dish.name) || bestDishScores.get(dish.name) < score) {
        bestDishScores.set(dish.name, score);
      }
    }

    for (const ing of payload.ingredients) {
      let bestLogit = -Infinity;
      for (let c = 0; c < ing.count; c++) {
        for (let v = 1; v < nViews; v++) {
          const logit = dotPrompt(v, pIdx) + payload.ingredientBias;
          if (logit > bestLogit) bestLogit = logit;
        }
        pIdx++;
      }
      const score = 1 / (1 + Math.exp(-bestLogit));
      if (!bestIngScores.has(ing.name) || bestIngScores.get(ing.name) < score) {
        bestIngScores.set(ing.name, score);
      }
    }
  }

  const topDishes = [...bestDishScores.entries()]
    .filter(([, score]) => score >= 0.12)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3);
  const topIngredients = [...bestIngScores.entries()]
    .filter(([, score]) => score >= 0.12)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5);

  return [...topDishes, ...topIngredients]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([name, score]) => `${encodeURIComponent(name)}|${score}`)
    .join('\n');
}

self.onmessage = async event => {
  const msg = event.data || {};
  if (msg.type === 'init') {
    try {
      await getFoodClassifier(msg.baseUrl);
      self.postMessage({ id: msg.id, type: 'init', ok: true });
    } catch (err) {
      self.postMessage({
        id: msg.id,
        type: 'init',
        ok: false,
        error: err?.message || String(err),
      });
    }
    return;
  }

  if (msg.type === 'classify') {
    try {
      const output = await classifyPhotoViews(msg.photoViews || [], msg.baseUrl);
      self.postMessage({ id: msg.id, type: 'result', ok: true, output });
    } catch (err) {
      self.postMessage({
        id: msg.id,
        type: 'result',
        ok: false,
        error: err?.message || String(err),
      });
    }
  }
};
