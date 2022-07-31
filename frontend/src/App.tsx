import React from 'react';
import logo from './logo.svg';
import './App.css';
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Lobby } from './components/Lobby'
import { PlayerRoute, PlayerProvider } from './auth/PlayerProvider'
import Register from './components/Register'
import Game from './components/Game';

function App() {
  return (
    <PlayerProvider>
      <BrowserRouter>
        <Routes>
          <Route
            path="/"
            element={
              <PlayerRoute>
                <Lobby />
              </PlayerRoute>}
          />
          <Route path="game">
            <Route path=":id" element={
              <PlayerRoute>
                <Game />
              </PlayerRoute>} />
          </Route>
          <Route
            path="/register"
            element={<Register />}
          />
        </Routes>
      </BrowserRouter>
    </PlayerProvider>

  );
}

export default App;
