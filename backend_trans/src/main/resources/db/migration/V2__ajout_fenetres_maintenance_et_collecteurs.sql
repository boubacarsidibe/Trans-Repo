-- Ajoute les tables des deux entites introduites apres la baseline V1 :
-- FenetreMaintenance (issue #160, fenetres de maintenance/silence programme)
-- et Collecteur (issue #157, redondance primaire/secondaire du collecteur
-- reseau). Genere via Hibernate ddl-auto=update contre un PostgreSQL reel
-- (V1 applique au prealable), puis verifie par pg_dump du schema resultant.

-- === maintenance ===

create table fenetres_maintenance (
    id uuid not null,
    equipement_id uuid not null,
    date_debut timestamp(6) not null,
    date_fin timestamp(6) not null,
    cree_par_id bigint not null,
    commentaire text,
    annulee boolean not null,
    creee_le timestamp(6) not null,
    primary key (id)
);

create index idx_fenetre_maintenance_equipement on fenetres_maintenance (equipement_id);

alter table fenetres_maintenance
    add constraint fk_fenetre_maintenance_equipement
    foreign key (equipement_id) references equipements;

alter table fenetres_maintenance
    add constraint fk_fenetre_maintenance_cree_par
    foreign key (cree_par_id) references app_users;

-- === collecteur ===

create table collecteurs (
    collecteur_id varchar(100) not null,
    actif boolean not null,
    dernier_heartbeat timestamp(6),
    primary key (collecteur_id)
);
