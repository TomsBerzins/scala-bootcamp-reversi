import React, {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  Routes,
  Route,
  Link,
  useNavigate,
  useLocation,
  Navigate,
  Outlet,
} from "react-router-dom";
import { useCookies } from 'react-cookie';
import Player from "../domain/Player";
interface IAuthContext {
  player: Player;
  signIn: (data: string, cb: () => void) => void;
}


const playerContext = React.createContext<IAuthContext>({} as IAuthContext);

function PlayerProvider({ children }: { children: ReactNode }) {

  const [cookies, setCookie, removeCookie] = useCookies(['player']);

  const signIn = (nameInput: string, cb: () => void) => {
    fetch("/create-player", {
      method: 'post',
      body: JSON.stringify({
        nickname: nameInput
      })
    })
    .then(res => res.json())
    .then(
      (result) => {
        setCookie('player', new Player(result.name, result.id), { path: '/' } );
        cb();
      }
    )
  }

  let player= new Player(
    cookies.player == null ? null : cookies.player.name,
    cookies.player == null ? null : cookies.player.id
  )


  return <playerContext.Provider value={{ player: player, signIn }}>{children}</playerContext.Provider>;
}

function PlayerRoute({children}: {children: any})  {

  const {player: player, signIn}= useContext(playerContext)

  if (player.id != null && player.name) {
    return children 
  } else {
    return <Navigate to="/register" />
  }
}


export { PlayerRoute, playerContext, PlayerProvider }