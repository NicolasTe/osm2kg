--Creates the schema "osm-links" and creates necessary tables for running the experiments.

CREATE SCHEMA osmlinks;

create table osmlinks.linkingexperiment
(
  id               serial not null
    constraint linkingexperiment_pkey
      primary key,
  osm              text,
  kg               text,
  model            text,
  correct          numeric,
  incorrect        numeric,
  not_found        numeric,
  precision        numeric,
  recall           numeric,
  f1               numeric,
  osm_embedding    text,
  threshold        numeric,
  features         text,
  classifier       text,
  lgdthreshold     integer,
  calculation_date timestamp default now()
);

create table osmlinks.linkingexperiment_folds
(
  experiment integer not null
    constraint linkingexperiment_folds_experiment_fkey
      references linkingexperiment,
  fold       integer not null,
  correct    integer,
  incorrect  integer,
  not_found  integer,
  precision  numeric,
  recall     numeric,
  f1         numeric,
  constraint linkingexperiment_folds_pkey
    primary key (experiment, fold)
);

create table osmlinks.candidates
(
  experiment   integer not null,
  fold         integer not null,
  osmid        text    not null,
  correct      boolean,
  kgid         text    not null,
  confidence   numeric,
  label        boolean,
  picked       boolean,
  no_candidate boolean,
  constraint candidates_pkey
    primary key (experiment, fold, osmid, kgid),
  constraint candidates_experiment_fkey
    foreign key (experiment, fold) references linkingexperiment_folds
);


create table osmlinks.classification_results
(
  experiment       integer not null,
  fold             integer not null,
  model            text,
  precision_cor    numeric,
  precision_incor  numeric,
  precision_micro  numeric,
  precision_macro  numeric,
  recall_cor       numeric,
  recall_incor     numeric,
  recall_micro     numeric,
  recall_macro     numeric,
  f1_cor           numeric,
  f1_incor         numeric,
  f1_micro         numeric,
  f1_macro         numeric,
  accuracy         numeric,
  confusion_matrix text,
  calculation_date timestamp default now(),
  constraint classification_results_pkey
    primary key (experiment, fold),
  constraint classification_results_experiment_fkey
    foreign key (experiment, fold) references linkingexperiment_folds
);

create table osmlinks.wikidata
(
  id       serial not null
    constraint wikidata_pkey
      primary key,
  wkid     text,
  name_en  text,
  name_ger text,
  name_fr  text,
  name_it  text,
  geometry geography(Point, 4326)
);