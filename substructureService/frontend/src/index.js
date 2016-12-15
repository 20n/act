import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';
import './index.css';
import {Router, Route, hashHistory} from 'react-router';

/*
ReactDOM.render(
  <App />,
  document.getElementById('root')
);
*/

ReactDOM.render(
  <Router history={hashHistory}>
    <Route path="/" component={App} />
  </Router>,
  document.getElementById('root')
);
