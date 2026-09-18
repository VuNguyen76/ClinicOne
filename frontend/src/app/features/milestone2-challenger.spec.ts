import { describe, it, expect } from 'vitest';
import * as fs from 'node:fs';
import * as path from 'node:path';

describe('Milestone 2 Unified Design System Challenger Suite', () => {
  const srcDir = path.resolve(__dirname, '../../');
  const stylesPath = path.resolve(srcDir, 'styles.css');
  const stylesContent = fs.readFileSync(stylesPath, 'utf-8');

  describe('Suite 1: CSS Theme Tokens & Medical Sky Blue Unification', () => {
    it('EMPIRICAL: defines --color-primary as Medical Sky Blue #0284c7', () => {
      expect(stylesContent).toMatch(/--color-primary:\s*#0284c7;/);
    });

    it('EMPIRICAL: defines --erp-brand-cyan as Medical Sky Blue #0284c7', () => {
      expect(stylesContent).toMatch(/--erp-brand-cyan:\s*#0284c7;/);
    });

    it('EMPIRICAL: purges legacy dark teal #0e3d48 and #082830 from styles.css', () => {
      expect(stylesContent).not.toContain('#0e3d48');
      expect(stylesContent).not.toContain('#082830');
      expect(stylesContent).not.toContain('#0a3140');
    });

    it('EMPIRICAL: configures @theme tokens without dark teal', () => {
      expect(stylesContent).toMatch(/--color-clinic-teal-900:\s*#0f172a;/);
      expect(stylesContent).toMatch(/--color-clinic-teal-600:\s*#0284c7;/);
    });
  });

  describe('Suite 2: ERP Buttons & Cards Standardization', () => {
    it('EMPIRICAL: .erp-btn-primary has #0284c7 bg, rounded-xl, shadow-xs, and min-h 2.25rem', () => {
      const btnPrimaryMatch = stylesContent.match(/\.erp-btn-primary\s*\{([^}]+)\}/);
      expect(btnPrimaryMatch).not.toBeNull();
      const body = btnPrimaryMatch![1];
      expect(body).toContain('#0284c7');
      expect(body).toMatch(/border-radius:\s*0\.75rem/);
      expect(body).toMatch(/min-height:\s*2\.25rem/);
      expect(body).toMatch(/box-shadow:\s*0 1px 2px 0 rgba\(0, 0, 0, 0\.05\)/);
    });

    it('EMPIRICAL: .erp-btn-secondary has rounded-xl and min-h 2.25rem', () => {
      const match = stylesContent.match(/\.erp-btn-secondary\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toMatch(/border-radius:\s*0\.75rem/);
      expect(body).toMatch(/min-height:\s*2\.25rem/);
    });

    it('EMPIRICAL: .erp-btn-outline has rounded-xl and min-h 2.25rem', () => {
      const match = stylesContent.match(/\.erp-btn-outline\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toMatch(/border-radius:\s*0\.75rem/);
      expect(body).toMatch(/min-height:\s*2\.25rem/);
    });

    it('EMPIRICAL: .erp-btn-danger has rounded-xl and min-h 2.25rem', () => {
      const match = stylesContent.match(/\.erp-btn-danger\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toMatch(/border-radius:\s*0\.75rem/);
      expect(body).toMatch(/min-height:\s*2\.25rem/);
    });

    it('EMPIRICAL: .erp-card has rounded-xl, border-slate-200, bg-white, and shadow-xs', () => {
      const match = stylesContent.match(/\.erp-card\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toMatch(/border-radius:\s*0\.75rem/);
      expect(body).toMatch(/border:\s*1px solid #e2e8f0/);
      expect(body).toMatch(/background-color:\s*#ffffff/);
      expect(body).toMatch(/box-shadow:\s*0 1px 2px 0 rgba\(0, 0, 0, 0\.05\)/);
    });
  });

  describe('Suite 3: Medical Status Badges Disambiguation', () => {
    it('EMPIRICAL: .erp-badge-completed has Emerald Green styling', () => {
      const match = stylesContent.match(/\.erp-badge-completed[^{]*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toContain('#a7f3d0');
      expect(body).toContain('#ecfdf5');
      expect(body).toContain('#047857');
    });

    it('EMPIRICAL: .erp-badge-in-progress has Medical Sky Blue styling', () => {
      const match = stylesContent.match(/\.erp-badge-in-progress\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toContain('#7dd3fc');
      expect(body).toContain('#f0f9ff');
      expect(body).toContain('#0284c7');
    });

    it('EMPIRICAL: .erp-badge-info has Medical Blue styling', () => {
      const match = stylesContent.match(/\.erp-badge-info\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toContain('#bae6fd');
      expect(body).toContain('#0369a1');
    });

    it('EMPIRICAL: .erp-badge-pending has Warm Amber styling', () => {
      const match = stylesContent.match(/\.erp-badge-pending[^{]*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toContain('#fed7aa');
      expect(body).toContain('#c2410c');
    });

    it('EMPIRICAL: .erp-badge-danger has Rose styling', () => {
      const match = stylesContent.match(/\.erp-badge-danger\s*\{([^}]+)\}/);
      expect(match).not.toBeNull();
      const body = match![1];
      expect(body).toContain('#fecdd3');
      expect(body).toContain('#be123c');
    });
  });

  describe('Suite 4: Zero Dark Teal (#0e3d48) in Frontend Source Files', () => {
    function getAllFiles(dir: string, ext: string[]): string[] {
      let results: string[] = [];
      const list = fs.readdirSync(dir);
      for (const file of list) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
          if (!file.startsWith('.') && file !== 'node_modules' && file !== 'dist') {
            results = results.concat(getAllFiles(fullPath, ext));
          }
        } else if (ext.some(e => file.endsWith(e))) {
          results.push(fullPath);
        }
      }
      return results;
    }

    it('EMPIRICAL: zero instances of #0e3d48 in any frontend template or css file', () => {
      const files = getAllFiles(srcDir, ['.html', '.css', '.ts']);
      const violations: string[] = [];

      for (const file of files) {
        // Exclude test challenger files that check for the string
        if (file.endsWith('milestone2-challenger.spec.ts')) {
          continue;
        }
        const content = fs.readFileSync(file, 'utf-8');
        if (content.includes('#0e3d48') || content.includes('#082830') || content.includes('#0a3140')) {
          violations.push(path.relative(srcDir, file));
        }
      }

      expect(violations).toEqual([]);
    });
  });
});
