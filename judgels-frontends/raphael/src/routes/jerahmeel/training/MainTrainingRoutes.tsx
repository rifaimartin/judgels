import * as React from 'react';
import { Route } from 'react-router';

import { FullPageLayout } from '../../../components/FullPageLayout/FullPageLayout';
import ContentWithSidebar, {
  ContentWithSidebarItem,
  ContentWithSidebarProps,
} from '../../../components/ContentWithSidebar/ContentWithSidebar';

import ProblemsetListPage from './problemsets/list/ProblemsetListPage/ProblemsetListPage';
import CoursePage from './courses/CoursePage';

const MainTrainingRoutes = () => {
  const sidebarItems: ContentWithSidebarItem[] = [
    {
      id: 'problemsets',
      titleIcon: 'projects',
      title: 'Problemsets',
      routeComponent: Route,
      component: ProblemsetListPage,
    },
    {
      id: 'courses',
      titleIcon: 'predictive-analysis',
      title: 'Courses',
      routeComponent: Route,
      component: CoursePage,
    },
  ];

  const contentWithSidebarProps: ContentWithSidebarProps = {
    title: 'Training',
    items: sidebarItems,
  };

  return (
    <FullPageLayout>
      <ContentWithSidebar {...contentWithSidebarProps} />
    </FullPageLayout>
  );
};

export default MainTrainingRoutes;
