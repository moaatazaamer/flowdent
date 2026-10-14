import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import OldRecords from './OldRecords';
import { LangProvider } from '../../context/LangContext';
import en from '../../i18n/en';
import { apiError, mockApi } from '../../test-utils';

const wrap = () => render(<LangProvider><MemoryRouter><OldRecords /></MemoryRouter></LangProvider>);

const preview = (over = {}) => ({
  fileName: 'old.xlsx', alreadyImportedAt: null,
  counts: { SALE: 2, PURCHASE: 1, EXPENSE: 0, CLIENT_BALANCE: 1, PROVIDER_BALANCE: 0 },
  totals: { SALE: 1500, PURCHASE: 700, EXPENSE: 0, CLIENT_BALANCE: 300, PROVIDER_BALANCE: 0 },
  years: [{ year: 2018, sales: 1500, purchases: 700, expenses: 0 }],
  newClients: ['Legacy Alpha Clinic'], newProviders: [],
  validRows: 4, problemRows: 0, errorCount: 0, errors: [], warnings: [], sample: [],
  ...over,
});

const choose = (name = 'old.xlsx') => {
  const input = screen.getByLabelText(en.old.choose);
  fireEvent.change(input, { target: { files: [new File(['x'], name)] } });
};

describe('Old records import', () => {
  test('check, then confirm imports the file and shows success', async () => {
    const api = mockApi({
      get: { '/admin/legacy/batches': [], '/admin/legacy/records': { total: 0, records: [] } },
      post: { '/admin/legacy/preview': preview(), '/admin/legacy/import': { id: 1 } },
    });
    wrap();
    expect(screen.getByText(en.old.check, { selector: 'button' })).toBeDisabled();

    choose();
    fireEvent.click(screen.getByText(en.old.check, { selector: 'button' }));
    expect(await screen.findByText(en.old.found)).toBeInTheDocument();
    expect(screen.getByText('2018')).toBeInTheDocument();
    expect(screen.getByText(/Legacy Alpha Clinic/)).toBeInTheDocument();

    fireEvent.click(screen.getByText(en.old.confirm, { selector: 'button' }));
    expect(await screen.findByText(en.old.done)).toBeInTheDocument();
    const call = api.post.mock.calls.find((c) => c[0] === '/admin/legacy/import');
    expect(call[1].get('skipInvalid')).toBe('false');
    expect(call[1].get('file').name).toBe('old.xlsx');
  });

  test('rows with problems block the import until the user chooses to skip them', async () => {
    const api = mockApi({
      get: { '/admin/legacy/batches': [], '/admin/legacy/records': { total: 0, records: [] } },
      post: {
        '/admin/legacy/preview': preview({ errorCount: 2, problemRows: 2, errors: ['Sales row 3: bad date'] }),
        '/admin/legacy/import': { id: 2 },
      },
    });
    wrap();
    choose();
    fireEvent.click(screen.getByText(en.old.check, { selector: 'button' }));
    expect(await screen.findByText(/Sales row 3: bad date/)).toBeInTheDocument();

    const confirm = screen.getByText(en.old.confirm, { selector: 'button' });
    expect(confirm).toBeDisabled();
    fireEvent.click(screen.getByLabelText(en.old.skip));
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(api.post.mock.calls.some((c) => c[0] === '/admin/legacy/import')).toBe(true));
    const call = api.post.mock.calls.find((c) => c[0] === '/admin/legacy/import');
    expect(call[1].get('skipInvalid')).toBe('true');
  });

  test('a file that was already imported cannot be imported again', async () => {
    mockApi({
      get: { '/admin/legacy/batches': [], '/admin/legacy/records': { total: 0, records: [] } },
      post: { '/admin/legacy/preview': preview({ alreadyImportedAt: '2026-10-01T10:00:00' }) },
    });
    wrap();
    choose();
    fireEvent.click(screen.getByText(en.old.check, { selector: 'button' }));
    await screen.findByText(/already imported/);
    expect(screen.getByText(en.old.confirm, { selector: 'button' })).toBeDisabled();
  });

  test('CSV files ask what they contain and send the type; server errors are shown', async () => {
    const api = mockApi({
      get: { '/admin/legacy/batches': [], '/admin/legacy/records': { total: 0, records: [] } },
      post: { '/admin/legacy/preview': apiError(400, 'Could not read this file') },
    });
    wrap();
    choose('expenses.csv');
    fireEvent.change(screen.getByLabelText(en.old.csvType), { target: { value: 'expenses' } });
    fireEvent.click(screen.getByText(en.old.check, { selector: 'button' }));
    expect(await screen.findByText('Could not read this file')).toBeInTheDocument();
    expect(api.post.mock.calls[0][1].get('type')).toBe('expenses');
  });

  test('past imports can be removed', async () => {
    const api = mockApi({
      get: {
        '/admin/legacy/batches': [{ id: 7, fileName: 'books-2018.xlsx', importedAt: '2026-10-01T10:00:00', sales: 2, purchases: 1, expenses: 1 }],
        '/admin/legacy/records': { total: 0, records: [] },
      },
    });
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    wrap();
    fireEvent.click(await screen.findByText(en.old.remove, { selector: 'button' }));
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/admin/legacy/batches/7'));
  });
});
