// Runs before every test file (Create React App convention).
import '@testing-library/jest-dom';
import { TextDecoder, TextEncoder } from 'util';

// react-router v7 needs these and Jest's jsdom does not provide them.
if (!global.TextEncoder) global.TextEncoder = TextEncoder;
if (!global.TextDecoder) global.TextDecoder = TextDecoder;

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute('dir');
  document.documentElement.removeAttribute('lang');
});
