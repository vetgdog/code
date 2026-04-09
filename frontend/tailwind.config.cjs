/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{js,vue}'],
  theme: {
    extend: {
      colors: {
        'on-tertiary-fixed-variant': '#723600',
        'on-surface': '#191c1e',
        'outline-variant': '#c2c6d6',
        'surface-container-highest': '#e0e3e5',
        'primary-container': '#2170e4',
        'on-primary-fixed-variant': '#004395',
        'surface-tint': '#005ac2',
        'on-secondary-container': '#586377',
        secondary: '#545f73',
        outline: '#727785',
        surface: '#f7f9fb',
        'on-tertiary': '#ffffff',
        tertiary: '#924700',
        'on-primary-container': '#fefcff',
        'secondary-fixed-dim': '#bcc7de',
        'on-secondary-fixed-variant': '#3c475a',
        'on-surface-variant': '#424754',
        'on-background': '#191c1e',
        background: '#f7f9fb',
        'on-secondary-fixed': '#111c2d',
        'on-tertiary-container': '#fffbff',
        'on-error': '#ffffff',
        'inverse-on-surface': '#eff1f3',
        'surface-dim': '#d8dadc',
        'secondary-fixed': '#d8e3fb',
        'primary-fixed': '#d8e2ff',
        'on-error-container': '#93000a',
        'surface-variant': '#e0e3e5',
        'on-tertiary-fixed': '#311400',
        'on-primary-fixed': '#001a42',
        'on-primary': '#ffffff',
        'tertiary-container': '#b75b00',
        'inverse-surface': '#2d3133',
        'surface-container-lowest': '#ffffff',
        error: '#ba1a1a',
        'surface-container': '#eceef0',
        'error-container': '#ffdad6',
        'tertiary-fixed-dim': '#ffb786',
        'surface-container-high': '#e6e8ea',
        primary: '#0058be',
        'surface-bright': '#f7f9fb',
        'secondary-container': '#d5e0f8',
        'tertiary-fixed': '#ffdcc6',
        'on-secondary': '#ffffff',
        'primary-fixed-dim': '#adc6ff',
        'surface-container-low': '#f2f4f6',
        'inverse-primary': '#adc6ff'
      },
      borderRadius: {
        DEFAULT: '0.125rem',
        lg: '0.25rem',
        xl: '0.5rem',
        full: '0.75rem'
      },
      fontFamily: {
        headline: ['Inter', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
        label: ['Inter', 'sans-serif']
      }
    }
  },
  plugins: []
};

