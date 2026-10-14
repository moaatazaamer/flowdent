import { useEffect } from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import { LangProvider } from './context/LangContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import api from './api';
import { setCurrency } from './utils/format';

import Navbar from './components/Navbar';
import AdminLayout from './components/AdminLayout';
import Footer from './components/Footer';

import Home from './pages/Home';
import Products from './pages/Products';
import Ora from './pages/Ora';
import Search from './pages/Search';
import ProductDetail from './pages/ProductDetail';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Login from './pages/Login';
import Register from './pages/Register';
import Orders from './pages/Orders';
import Profile from './pages/Profile';

import AdminDashboard from './pages/admin/AdminDashboard';
import AdminCategories from './pages/admin/AdminCategories';
import AdminProducts from './pages/admin/AdminProducts';
import AdminOrders from './pages/admin/AdminOrders';
import AdminImport from './pages/admin/AdminImport';
import OldRecords from './pages/admin/OldRecords';
import AdminClients from './pages/admin/ops/AdminClients';
import AdminProviders from './pages/admin/ops/AdminProviders';
import AdminPurchases from './pages/admin/ops/AdminPurchases';
import AdminReturns from './pages/admin/ops/AdminReturns';
import AdminStock from './pages/admin/ops/AdminStock';
import AdminExpenses from './pages/admin/ops/AdminExpenses';
import AdminFinance from './pages/admin/ops/AdminFinance';

function Shop({ children }) {
  return (
    <>
      <Navbar />
      <main className="shop-main">{children}</main>
      <Footer />
    </>
  );
}

function RequireAuth({ children }) {
  const { isAuthenticated, ready } = useAuth();
  if (!ready) return null;
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

function RequireStaff({ children }) {
  const { isStaff, ready } = useAuth();
  if (!ready) return null;
  return isStaff ? children : <Navigate to="/login" replace />;
}

function RequireAdmin({ children }) {
  const { isAdmin } = useAuth();
  return isAdmin ? children : <Navigate to="/admin/orders" replace />;
}

function CurrencyLoader() {
  useEffect(() => {
    api
      .get('/payment/config')
      .then((res) => setCurrency(res.data.currency))
      .catch(() => {});
  }, []);
  return null;
}

export default function App() {
  return (
    <LangProvider>
      <AuthProvider>
        <CartProvider>
          <Router>
            <CurrencyLoader />
            <Routes>
              <Route path="/" element={<Shop><Home /></Shop>} />
              <Route path="/products" element={<Shop><Products /></Shop>} />
              <Route path="/ora" element={<Shop><Ora /></Shop>} />
              <Route path="/search" element={<Shop><Search /></Shop>} />
              <Route path="/products/:id" element={<Shop><ProductDetail /></Shop>} />
              <Route path="/cart" element={<Shop><Cart /></Shop>} />
              <Route path="/checkout" element={<Shop><Checkout /></Shop>} />
              <Route path="/login" element={<Shop><Login /></Shop>} />
              <Route path="/register" element={<Shop><Register /></Shop>} />
              <Route path="/orders" element={<Shop><RequireAuth><Orders /></RequireAuth></Shop>} />
              <Route path="/profile" element={<Shop><RequireAuth><Profile /></RequireAuth></Shop>} />

              <Route
                path="/admin"
                element={
                  <RequireStaff>
                    <AdminLayout />
                  </RequireStaff>
                }
              >
                <Route index element={<AdminDashboard />} />
                <Route path="categories" element={<RequireAdmin><AdminCategories /></RequireAdmin>} />
                <Route path="products" element={<RequireAdmin><AdminProducts /></RequireAdmin>} />
                <Route path="orders" element={<AdminOrders />} />
                <Route path="clients" element={<RequireAdmin><AdminClients /></RequireAdmin>} />
                <Route path="providers" element={<RequireAdmin><AdminProviders /></RequireAdmin>} />
                <Route path="purchases" element={<RequireAdmin><AdminPurchases /></RequireAdmin>} />
                <Route path="returns" element={<RequireAdmin><AdminReturns /></RequireAdmin>} />
                <Route path="stock" element={<RequireAdmin><AdminStock /></RequireAdmin>} />
                <Route path="expenses" element={<RequireAdmin><AdminExpenses /></RequireAdmin>} />
                <Route path="finance" element={<RequireAdmin><AdminFinance /></RequireAdmin>} />
                <Route path="customers" element={<Navigate to="/admin/clients" replace />} />
                <Route path="import" element={<RequireAdmin><AdminImport /></RequireAdmin>} />
                <Route path="old-records" element={<RequireAdmin><OldRecords /></RequireAdmin>} />
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Router>
        </CartProvider>
      </AuthProvider>
    </LangProvider>
  );
}
