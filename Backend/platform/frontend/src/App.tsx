import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { CreateProjectPage } from './pages/CreateProjectPage'
import { LoginPage } from './pages/LoginPage'
import { PdfViewerPage } from './pages/PdfViewerPage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { UsersPage } from './pages/UsersPage'
import { getUser } from './lib/auth'

function DirectorRoute({ children }: { children: ReactNode }) {
  const user = getUser()
  if (user?.role !== 'DIRECTOR') {
    return <Navigate to="/projects" replace />
  }
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route
          path="projects/:projectId/documents/:documentId"
          element={<PdfViewerPage />}
        />
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/projects" replace />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="projects/new" element={<CreateProjectPage />} />
          <Route path="projects/:projectId" element={<ProjectDetailPage />} />
          <Route
            path="users"
            element={
              <DirectorRoute>
                <UsersPage />
              </DirectorRoute>
            }
          />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/projects" replace />} />
    </Routes>
  )
}
